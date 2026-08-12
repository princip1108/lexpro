package com.lexpro.lexprobackend.dossier.service;

import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.dossier.domain.DossierFolder;
import com.lexpro.lexprobackend.dossier.domain.FileTag;
import com.lexpro.lexprobackend.dossier.mapper.DossierFolderMapper;
import com.lexpro.lexprobackend.dossier.mapper.FileTagMapper;
import com.lexpro.lexprobackend.dossier.web.dto.CreateDossierFolderRequest;
import com.lexpro.lexprobackend.dossier.web.dto.CreateFileTagRequest;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFolderResponse;
import com.lexpro.lexprobackend.dossier.web.dto.FileTagResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DossierCatalogService {

    private final DossierFolderMapper folderMapper;
    private final FileTagMapper tagMapper;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public DossierCatalogService(DossierFolderMapper folderMapper, FileTagMapper tagMapper,
                                 CaseAccessService caseAccessService, AuditService auditService) {
        this.folderMapper = folderMapper;
        this.tagMapper = tagMapper;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DossierFolderResponse> folderTree(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        List<DossierFolder> folders = folderMapper.selectByCase(caseId);
        Map<Long, DossierFolderResponse> nodes = new LinkedHashMap<>();
        folders.forEach(folder -> nodes.put(folder.getFolderId(),
                DossierFolderResponse.from(folder, new ArrayList<>())));
        List<DossierFolderResponse> roots = new ArrayList<>();
        for (DossierFolder folder : folders) {
            DossierFolderResponse node = nodes.get(folder.getFolderId());
            if (folder.getParentFolderId() == null) {
                roots.add(node);
            } else {
                DossierFolderResponse parent = nodes.get(folder.getParentFolderId());
                if (parent == null) {
                    throw new IllegalStateException("Dossier folder parent is missing");
                }
                parent.children().add(node);
            }
        }
        return roots;
    }

    @Transactional
    public DossierFolderResponse createFolder(long userId, long caseId, CreateDossierFolderRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        if (request.parentFolderId() != null) {
            requireFolder(caseId, request.parentFolderId());
        }
        DossierFolder folder = new DossierFolder();
        folder.setCaseId(caseId);
        folder.setParentFolderId(request.parentFolderId());
        folder.setFolderName(request.folderName().trim());
        folder.setSortNo(request.sortNo());
        folder.setCreatedBy(userId);
        try {
            folderMapper.insert(folder);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Folder already exists", "DOSSIER_FOLDER_CONFLICT",
                    "A folder with this name already exists at the selected level");
        }
        auditService.record(new AuditEvent(userId, caseId, "DOSSIER_FOLDER_CREATED", "DOSSIER_FOLDER",
                String.valueOf(folder.getFolderId()), AuditResult.SUCCESS,
                Map.of("folderName", folder.getFolderName())));
        return DossierFolderResponse.from(folderMapper.selectByCaseAndId(caseId, folder.getFolderId()), List.of());
    }

    @Transactional(readOnly = true)
    public List<FileTagResponse> listTags(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        return tagMapper.selectByCase(caseId).stream().map(FileTagResponse::from).toList();
    }

    @Transactional
    public FileTagResponse createTag(long userId, long caseId, CreateFileTagRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        FileTag tag = new FileTag();
        tag.setCaseId(caseId);
        tag.setTagName(request.tagName().trim());
        tag.setColor(request.color() == null ? null : request.color().toUpperCase());
        tag.setCreatedBy(userId);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Tag already exists", "DOSSIER_TAG_CONFLICT",
                    "A tag with this name already exists in the case");
        }
        auditService.record(new AuditEvent(userId, caseId, "DOSSIER_TAG_CREATED", "FILE_TAG",
                String.valueOf(tag.getTagId()), AuditResult.SUCCESS, Map.of("tagName", tag.getTagName())));
        return FileTagResponse.from(tagMapper.selectByCaseAndId(caseId, tag.getTagId()));
    }

    @Transactional(readOnly = true)
    public void requireFolderIfPresent(long caseId, Long folderId) {
        if (folderId != null) {
            requireFolder(caseId, folderId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> validateTagIds(long caseId, List<Long> requestedTagIds) {
        if (requestedTagIds == null || requestedTagIds.isEmpty()) {
            return List.of();
        }
        if (requestedTagIds.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many dossier tags", "DOSSIER_TAG_LIMIT",
                    "At most 50 tags may be assigned to one file");
        }
        List<Long> distinct = requestedTagIds.stream().distinct().toList();
        Map<Long, FileTag> existing = new HashMap<>();
        tagMapper.selectByCase(caseId).forEach(tag -> existing.put(tag.getTagId(), tag));
        if (distinct.stream().anyMatch(tagId -> !existing.containsKey(tagId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid dossier tag", "DOSSIER_TAG_INVALID",
                    "One or more tags do not belong to the requested case");
        }
        return distinct;
    }

    private DossierFolder requireFolder(long caseId, long folderId) {
        DossierFolder folder = folderMapper.selectByCaseAndId(caseId, folderId);
        if (folder == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid dossier folder", "DOSSIER_FOLDER_INVALID",
                    "The selected folder does not belong to the requested case");
        }
        return folder;
    }
}
