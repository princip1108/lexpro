package com.lexpro.lexprobackend.dossier.web;

import com.lexpro.lexprobackend.dossier.service.DossierCatalogService;
import com.lexpro.lexprobackend.dossier.service.DossierFileService;
import com.lexpro.lexprobackend.dossier.web.dto.CreateDossierFolderRequest;
import com.lexpro.lexprobackend.dossier.web.dto.CreateFileTagRequest;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFileResponse;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFolderResponse;
import com.lexpro.lexprobackend.dossier.web.dto.FileTagResponse;
import com.lexpro.lexprobackend.dossier.web.dto.UpdateDossierFileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/dossier")
@SecurityRequirement(name = "bearerAuth")
public class DossierController {

    private final DossierCatalogService catalogService;
    private final DossierFileService fileService;

    public DossierController(DossierCatalogService catalogService, DossierFileService fileService) {
        this.catalogService = catalogService;
        this.fileService = fileService;
    }

    @GetMapping("/folders")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<DossierFolderResponse> folders(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return catalogService.folderTree(userId(jwt), caseId);
    }

    @PostMapping("/folders")
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    public ResponseEntity<DossierFolderResponse> createFolder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody CreateDossierFolderRequest request) {
        DossierFolderResponse response = catalogService.createFolder(userId(jwt), caseId, request);
        return ResponseEntity.created(URI.create(base(caseId) + "/folders/" + response.folderId())).body(response);
    }

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<FileTagResponse> tags(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return catalogService.listTags(userId(jwt), caseId);
    }

    @PostMapping("/tags")
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    public ResponseEntity<FileTagResponse> createTag(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody CreateFileTagRequest request) {
        FileTagResponse response = catalogService.createTag(userId(jwt), caseId, request);
        return ResponseEntity.created(URI.create(base(caseId) + "/tags/" + response.tagId())).body(response);
    }

    @GetMapping("/files")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<DossierFileResponse> files(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return fileService.list(userId(jwt), caseId, folderId, includeDeleted);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    @Operation(summary = "Upload one dossier file")
    public ResponseEntity<DossierFileResponse> upload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(name = "tagId", required = false) List<Long> tagIds,
            @RequestParam("file") MultipartFile file) {
        DossierFileResponse response = fileService.upload(userId(jwt), caseId, folderId, tagIds, file);
        return ResponseEntity.created(URI.create(base(caseId) + "/files/" + response.dossierId())).body(response);
    }

    @PutMapping("/files/{dossierId}")
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    public DossierFileResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId,
            @Valid @RequestBody UpdateDossierFileRequest request) {
        return fileService.update(userId(jwt), caseId, dossierId, request);
    }

    @DeleteMapping("/files/{dossierId}")
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId) {
        fileService.softDelete(userId(jwt), caseId, dossierId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/{dossierId}/restore")
    @PreAuthorize("hasAuthority('DOSSIER_MANAGE')")
    public DossierFileResponse restore(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId) {
        return fileService.restore(userId(jwt), caseId, dossierId);
    }

    @GetMapping("/files/{dossierId}/content")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId) {
        DossierFileService.DossierDownload download = fileService.download(userId(jwt), caseId, dossierId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }

    private String base(long caseId) { return "/api/v1/cases/" + caseId + "/dossier"; }
}
