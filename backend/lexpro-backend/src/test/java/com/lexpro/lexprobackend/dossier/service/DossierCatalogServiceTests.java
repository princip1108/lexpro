package com.lexpro.lexprobackend.dossier.service;

import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.dossier.domain.DossierFolder;
import com.lexpro.lexprobackend.dossier.domain.FileTag;
import com.lexpro.lexprobackend.dossier.mapper.DossierFolderMapper;
import com.lexpro.lexprobackend.dossier.mapper.FileTagMapper;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFolderResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DossierCatalogServiceTests {

    @Test
    void shouldBuildNestedFolderTreeAfterCaseAccessCheck() {
        DossierFolderMapper folderMapper = mock(DossierFolderMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        DossierFolder root = folder(1L, null, "Root");
        DossierFolder child = folder(2L, 1L, "Child");
        when(folderMapper.selectByCase(9L)).thenReturn(List.of(root, child));
        DossierCatalogService service = new DossierCatalogService(folderMapper, mock(FileTagMapper.class),
                accessService, mock(AuditService.class));

        List<DossierFolderResponse> result = service.folderTree(7L, 9L);

        assertEquals(1, result.size());
        assertEquals("Root", result.getFirst().folderName());
        assertEquals("Child", result.getFirst().children().getFirst().folderName());
        verify(accessService).requireRead(9L, 7L);
    }

    @Test
    void shouldRejectTagFromAnotherCase() {
        FileTagMapper tagMapper = mock(FileTagMapper.class);
        FileTag valid = new FileTag();
        valid.setTagId(3L);
        valid.setCaseId(9L);
        when(tagMapper.selectByCase(9L)).thenReturn(List.of(valid));
        DossierCatalogService service = new DossierCatalogService(mock(DossierFolderMapper.class), tagMapper,
                mock(CaseAccessService.class), mock(AuditService.class));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.validateTagIds(9L, List.of(3L, 99L)));

        assertEquals("DOSSIER_TAG_INVALID", exception.getErrorCode());
    }

    private DossierFolder folder(long folderId, Long parentId, String name) {
        DossierFolder folder = new DossierFolder();
        folder.setFolderId(folderId);
        folder.setCaseId(9L);
        folder.setParentFolderId(parentId);
        folder.setFolderName(name);
        return folder;
    }
}
