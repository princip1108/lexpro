package com.lexpro.lexprobackend.organization.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.organization.web.dto.OrganizationTreeNodeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildOrganizationTree() {
        OrganizationUnitMapper mapper = mock(OrganizationUnitMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                organization(1L, null, "ROOT"),
                organization(2L, 1L, "TEAM")
        ));
        OrganizationService service = new OrganizationService(mapper);

        List<OrganizationTreeNodeResponse> tree = service.getTree();

        assertEquals(1, tree.size());
        assertEquals("ROOT", tree.getFirst().code());
        assertEquals("TEAM", tree.getFirst().children().getFirst().code());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectCycles() {
        OrganizationUnitMapper mapper = mock(OrganizationUnitMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                organization(1L, 2L, "ONE"),
                organization(2L, 1L, "TWO")
        ));
        OrganizationService service = new OrganizationService(mapper);

        assertThrows(IllegalStateException.class, service::getTree);
    }

    private OrganizationUnit organization(long id, Long parentId, String code) {
        OrganizationUnit organization = new OrganizationUnit();
        organization.setOrganizationId(id);
        organization.setParentOrganizationId(parentId);
        organization.setOrganizationCode(code);
        organization.setOrganizationName(code);
        organization.setStatus("ACTIVE");
        return organization;
    }
}
