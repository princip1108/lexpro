package com.lexpro.lexprobackend.dossier.service;

import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.dossier.config.DossierStorageProperties;
import com.lexpro.lexprobackend.dossier.domain.EvidenceFile;
import com.lexpro.lexprobackend.dossier.mapper.EvidenceFileMapper;
import com.lexpro.lexprobackend.dossier.mapper.EvidenceFileTagMapper;
import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DossierFileServiceTests {

    @Test
    void shouldUploadMetadataWithoutExposingObjectKey() throws Exception {
        EvidenceFileMapper fileMapper = mock(EvidenceFileMapper.class);
        EvidenceFileTagMapper fileTagMapper = mock(EvidenceFileTagMapper.class);
        CaseAccessService caseAccessService = mock(CaseAccessService.class);
        DossierCatalogService catalogService = mock(DossierCatalogService.class);
        DossierStorage storage = mock(DossierStorage.class);
        AuditService auditService = mock(AuditService.class);
        DossierStorageProperties properties = properties();
        AtomicReference<EvidenceFile> inserted = new AtomicReference<>();
        when(storage.put(eq(9L), eq("pdf"), any(InputStream.class), eq(properties.getMaxFileSize().toBytes())))
                .thenReturn(new DossierStorage.StoredObject("cases/9/private-object.pdf", 4L, "abc123"));
        when(fileMapper.insert(any(EvidenceFile.class))).thenAnswer(invocation -> {
            EvidenceFile file = invocation.getArgument(0);
            file.setDossierId(5L);
            file.setUploadedAt(OffsetDateTime.now());
            file.setUpdatedAt(file.getUploadedAt());
            inserted.set(file);
            return 1;
        });
        when(fileMapper.selectByCaseAndId(9L, 5L)).thenAnswer(ignored -> {
            EvidenceFile file = inserted.get();
            file.setUploadUserName("Uploader");
            return file;
        });
        when(fileTagMapper.selectByCase(9L)).thenReturn(List.of());
        DossierFileService service = new DossierFileService(fileMapper, fileTagMapper, caseAccessService,
                catalogService, storage, properties, auditService);

        DossierFileResponse response = service.upload(7L, 9L, null, List.of(),
                new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-1.7".getBytes()));

        assertEquals(5L, response.dossierId());
        assertEquals("evidence.pdf", response.fileName());
        assertEquals("abc123", response.fileHash());
        assertFalse(response.toString().contains("private-object"));
        verify(caseAccessService).requireEdit(9L, 7L);
    }

    @Test
    void shouldRejectDisallowedExtensionBeforeWritingStorage() throws Exception {
        EvidenceFileMapper fileMapper = mock(EvidenceFileMapper.class);
        EvidenceFileTagMapper fileTagMapper = mock(EvidenceFileTagMapper.class);
        DossierStorage storage = mock(DossierStorage.class);
        DossierFileService service = new DossierFileService(fileMapper, fileTagMapper,
                mock(CaseAccessService.class), mock(DossierCatalogService.class), storage,
                properties(), mock(AuditService.class));

        ApiException exception = assertThrows(ApiException.class, () -> service.upload(7L, 9L, null, List.of(),
                new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1})));

        assertEquals("DOSSIER_EXTENSION_NOT_ALLOWED", exception.getErrorCode());
        verify(storage, never()).put(anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldRejectSpoofedPdfBeforeWritingStorage() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        DossierFileService service = new DossierFileService(mock(EvidenceFileMapper.class),
                mock(EvidenceFileTagMapper.class), mock(CaseAccessService.class),
                mock(DossierCatalogService.class), storage, properties(), mock(AuditService.class));

        ApiException exception = assertThrows(ApiException.class, () -> service.upload(7L, 9L, null, List.of(),
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes())));

        assertEquals("DOSSIER_FILE_SIGNATURE_MISMATCH", exception.getErrorCode());
        verify(storage, never()).put(anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldNeverPhysicallyDeleteOnSoftDelete() throws Exception {
        EvidenceFileMapper fileMapper = mock(EvidenceFileMapper.class);
        EvidenceFile file = new EvidenceFile();
        file.setDossierId(5L);
        file.setCaseId(9L);
        file.setFileName("evidence.pdf");
        file.setFileStatus("ACTIVE");
        file.setFileUrl("cases/9/private-object.pdf");
        when(fileMapper.selectByCaseAndId(9L, 5L)).thenReturn(file);
        when(fileMapper.softDelete(9L, 5L, 7L)).thenReturn(1);
        DossierStorage storage = mock(DossierStorage.class);
        DossierFileService service = new DossierFileService(fileMapper, mock(EvidenceFileTagMapper.class),
                mock(CaseAccessService.class), mock(DossierCatalogService.class), storage,
                properties(), mock(AuditService.class));

        service.softDelete(7L, 9L, 5L);

        verify(storage, never()).delete(any());
    }

    private DossierStorageProperties properties() {
        DossierStorageProperties properties = new DossierStorageProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(25));
        return properties;
    }
}
