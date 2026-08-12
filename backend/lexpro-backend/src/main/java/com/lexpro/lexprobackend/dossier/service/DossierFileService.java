package com.lexpro.lexprobackend.dossier.service;

import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.dossier.config.DossierStorageProperties;
import com.lexpro.lexprobackend.dossier.domain.EvidenceFile;
import com.lexpro.lexprobackend.dossier.mapper.EvidenceFileMapper;
import com.lexpro.lexprobackend.dossier.mapper.EvidenceFileTagMapper;
import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.dossier.storage.StorageLimitExceededException;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFileResponse;
import com.lexpro.lexprobackend.dossier.web.dto.FileTagResponse;
import com.lexpro.lexprobackend.dossier.web.dto.UpdateDossierFileRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DossierFileService {

    private static final Logger log = LoggerFactory.getLogger(DossierFileService.class);
    private static final Map<String, Set<String>> EXPECTED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png"))
    );

    private final EvidenceFileMapper fileMapper;
    private final EvidenceFileTagMapper fileTagMapper;
    private final CaseAccessService caseAccessService;
    private final DossierCatalogService catalogService;
    private final DossierStorage storage;
    private final DossierStorageProperties properties;
    private final AuditService auditService;

    public DossierFileService(EvidenceFileMapper fileMapper, EvidenceFileTagMapper fileTagMapper,
                              CaseAccessService caseAccessService, DossierCatalogService catalogService,
                              DossierStorage storage, DossierStorageProperties properties,
                              AuditService auditService) {
        this.fileMapper = fileMapper;
        this.fileTagMapper = fileTagMapper;
        this.caseAccessService = caseAccessService;
        this.catalogService = catalogService;
        this.storage = storage;
        this.properties = properties;
        this.auditService = auditService;
        if (properties.getMaxFileSize() == null || properties.getMaxFileSize().toBytes() <= 0) {
            throw new IllegalStateException("lexpro.dossier.max-file-size must be positive");
        }
    }

    @Transactional(readOnly = true)
    public List<DossierFileResponse> list(long userId, long caseId, Long folderId, boolean includeDeleted) {
        caseAccessService.requireRead(caseId, userId);
        catalogService.requireFolderIfPresent(caseId, folderId);
        Map<Long, List<FileTagResponse>> tags = tagsByFile(caseId);
        return fileMapper.selectByCase(caseId, folderId, false, includeDeleted).stream()
                .map(file -> response(file, tags)).toList();
    }

    @Transactional
    public DossierFileResponse upload(long userId, long caseId, Long folderId, List<Long> requestedTagIds,
                                      MultipartFile multipartFile) {
        caseAccessService.requireEdit(caseId, userId);
        catalogService.requireFolderIfPresent(caseId, folderId);
        List<Long> tagIds = catalogService.validateTagIds(caseId, requestedTagIds);
        UploadMetadata upload = validateUpload(multipartFile);

        DossierStorage.StoredObject stored;
        try (InputStream raw = multipartFile.getInputStream();
             BufferedInputStream content = new BufferedInputStream(raw)) {
            validateSignature(upload.extension(), content);
            stored = storage.put(caseId, upload.extension(), content, maxBytes());
        } catch (StorageLimitExceededException exception) {
            throw fileTooLarge();
        } catch (IOException exception) {
            throw storageFailure("The file could not be stored");
        }

        try {
            EvidenceFile file = new EvidenceFile();
            file.setCaseId(caseId);
            file.setFolderId(folderId);
            file.setFileName(upload.fileName());
            file.setFileType(upload.contentType());
            file.setFileUrl(stored.objectKey());
            file.setUploadUserId(userId);
            file.setFileSize(stored.size());
            file.setFileHash(stored.sha256());
            file.setFileStatus("ACTIVE");
            fileMapper.insert(file);
            replaceTagLinks(file.getDossierId(), userId, tagIds);
            auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FILE_UPLOADED", "EVIDENCE_FILE",
                    String.valueOf(file.getDossierId()), AuditResult.SUCCESS,
                    Map.of("fileName", file.getFileName(), "fileSize", file.getFileSize(),
                            "sha256", file.getFileHash())));
            return getResponse(caseId, file.getDossierId());
        } catch (RuntimeException exception) {
            compensateStoredObject(stored.objectKey());
            throw exception;
        }
    }

    @Transactional
    public DossierFileResponse update(long userId, long caseId, long dossierId,
                                      UpdateDossierFileRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        EvidenceFile file = requireFile(caseId, dossierId);
        requireActive(file);
        catalogService.requireFolderIfPresent(caseId, request.folderId());
        List<Long> tagIds = catalogService.validateTagIds(caseId, request.tagIds());
        String fileName = safeFileName(request.fileName());
        if (!extension(fileName).equals(extension(file.getFileName()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name", "DOSSIER_EXTENSION_CHANGE",
                    "Renaming a dossier file cannot change its extension");
        }
        if (fileMapper.updateMetadata(caseId, dossierId, fileName, request.folderId()) != 1) {
            throw fileNotFound();
        }
        replaceTagLinks(dossierId, userId, tagIds);
        auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FILE_UPDATED", "EVIDENCE_FILE",
                String.valueOf(dossierId), AuditResult.SUCCESS,
                Map.of("fileName", fileName, "tagCount", tagIds.size())));
        return getResponse(caseId, dossierId);
    }

    @Transactional
    public void softDelete(long userId, long caseId, long dossierId) {
        caseAccessService.requireEdit(caseId, userId);
        EvidenceFile file = requireFile(caseId, dossierId);
        requireActive(file);
        if (fileMapper.softDelete(caseId, dossierId, userId) != 1) {
            throw fileNotFound();
        }
        auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FILE_DELETED", "EVIDENCE_FILE",
                String.valueOf(dossierId), AuditResult.SUCCESS, Map.of("fileName", file.getFileName())));
    }

    @Transactional
    public DossierFileResponse restore(long userId, long caseId, long dossierId) {
        caseAccessService.requireEdit(caseId, userId);
        EvidenceFile file = requireFile(caseId, dossierId);
        if (!"DELETED".equals(file.getFileStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "File is active", "DOSSIER_FILE_ACTIVE",
                    "Only a soft-deleted dossier file can be restored");
        }
        if (fileMapper.restore(caseId, dossierId) != 1) {
            throw fileNotFound();
        }
        auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FILE_RESTORED", "EVIDENCE_FILE",
                String.valueOf(dossierId), AuditResult.SUCCESS, Map.of("fileName", file.getFileName())));
        return getResponse(caseId, dossierId);
    }

    @Transactional
    public DossierDownload download(long userId, long caseId, long dossierId) {
        caseAccessService.requireRead(caseId, userId);
        EvidenceFile file = requireFile(caseId, dossierId);
        requireActive(file);
        try {
            DossierStorage.StoredResource stored = storage.get(file.getFileUrl());
            auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FILE_DOWNLOADED", "EVIDENCE_FILE",
                    String.valueOf(dossierId), AuditResult.SUCCESS,
                    Map.of("fileName", file.getFileName(), "fileSize", stored.size())));
            return new DossierDownload(file.getFileName(), file.getFileType(), stored.size(), stored.resource());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.GONE, "File content unavailable", "DOSSIER_CONTENT_MISSING",
                    "The dossier metadata exists but its stored content is unavailable");
        }
    }

    private UploadMetadata validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Empty file", "DOSSIER_FILE_EMPTY",
                    "A non-empty file is required");
        }
        if (file.getSize() > maxBytes()) {
            throw fileTooLarge();
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String extension = extension(fileName);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type",
                    "DOSSIER_EXTENSION_NOT_ALLOWED", "The file extension is not allowed");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!properties.getAllowedContentTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type",
                    "DOSSIER_CONTENT_TYPE_NOT_ALLOWED", "The file content type is not allowed");
        }
        Set<String> expected = EXPECTED_CONTENT_TYPES.get(extension);
        if (!MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)
                && expected != null && !expected.contains(contentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type mismatch",
                    "DOSSIER_FILE_TYPE_MISMATCH", "The file extension and content type do not match");
        }
        return new UploadMetadata(fileName, extension, contentType);
    }

    private void validateSignature(String extension, BufferedInputStream content) throws IOException {
        content.mark(1024);
        byte[] header = content.readNBytes(512);
        content.reset();
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "jpg", "jpeg" -> startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "docx", "xlsx", "pptx" -> startsWith(header, new byte[]{0x50, 0x4b, 0x03, 0x04});
            case "doc", "xls", "ppt" -> startsWith(header, new byte[]{
                    (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
            });
            case "txt" -> containsNoNullByte(header);
            default -> true;
        };
        if (!valid) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File signature mismatch",
                    "DOSSIER_FILE_SIGNATURE_MISMATCH",
                    "The file content does not match its extension");
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsNoNullByte(byte[] value) {
        for (byte item : value) {
            if (item == 0) {
                return false;
            }
        }
        return true;
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name", "DOSSIER_FILE_NAME_INVALID",
                    "The uploaded file must have a name");
        }
        String slashNormalized = originalFilename.replace('\\', '/');
        String name = slashNormalized.substring(slashNormalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name", "DOSSIER_FILE_NAME_INVALID",
                    "The file name is blank, unsafe or longer than 255 characters");
        }
        extension(name);
        return name;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name", "DOSSIER_EXTENSION_REQUIRED",
                    "The file name must include an extension");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String normalized = MediaType.parseMediaType(value).toString().toLowerCase(Locale.ROOT);
            int separator = normalized.indexOf(';');
            return separator < 0 ? normalized : normalized.substring(0, separator);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid content type",
                    "DOSSIER_CONTENT_TYPE_INVALID", "The file content type is invalid");
        }
    }

    private void replaceTagLinks(long dossierId, long userId, List<Long> tagIds) {
        fileTagMapper.deleteByDossierId(dossierId);
        tagIds.forEach(tagId -> fileTagMapper.insertLink(dossierId, tagId, userId));
    }

    private Map<Long, List<FileTagResponse>> tagsByFile(long caseId) {
        Map<Long, List<FileTagResponse>> tags = new HashMap<>();
        fileTagMapper.selectByCase(caseId).forEach(row -> tags
                .computeIfAbsent(row.dossierId(), ignored -> new ArrayList<>())
                .add(FileTagResponse.from(row.toTag())));
        return tags;
    }

    private DossierFileResponse getResponse(long caseId, long dossierId) {
        EvidenceFile file = requireFile(caseId, dossierId);
        return response(file, tagsByFile(caseId));
    }

    private DossierFileResponse response(EvidenceFile file, Map<Long, List<FileTagResponse>> tags) {
        return DossierFileResponse.from(file, tags.getOrDefault(file.getDossierId(), List.of()));
    }

    private EvidenceFile requireFile(long caseId, long dossierId) {
        EvidenceFile file = fileMapper.selectByCaseAndId(caseId, dossierId);
        if (file == null) {
            throw fileNotFound();
        }
        return file;
    }

    private void requireActive(EvidenceFile file) {
        if (!"ACTIVE".equals(file.getFileStatus())) {
            throw new ApiException(HttpStatus.GONE, "File was deleted", "DOSSIER_FILE_DELETED",
                    "The dossier file has been soft deleted");
        }
    }

    private long maxBytes() { return properties.getMaxFileSize().toBytes(); }

    private void compensateStoredObject(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (IOException cleanupException) {
            log.error("dossier_upload_compensation_failed", cleanupException);
        }
    }

    private ApiException fileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Dossier file not found", "DOSSIER_FILE_NOT_FOUND",
                "The requested dossier file does not exist");
    }

    private ApiException fileTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "DOSSIER_FILE_TOO_LARGE",
                "The file exceeds the configured upload limit");
    }

    private ApiException storageFailure(String detail) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "File storage failed", "DOSSIER_STORAGE_FAILED",
                detail);
    }

    private record UploadMetadata(String fileName, String extension, String contentType) {
    }

    public record DossierDownload(String fileName, String contentType, long size, Resource resource) {
    }
}
