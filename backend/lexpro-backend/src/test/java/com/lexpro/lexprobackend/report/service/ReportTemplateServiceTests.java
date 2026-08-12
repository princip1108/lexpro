package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportTemplateServiceTests {

    @Test
    void shouldDisablePreviousActiveVersionBeforeActivation() {
        ReportTemplateMapper mapper = mock(ReportTemplateMapper.class);
        ReportTemplate draft = template("DRAFT");
        ReportTemplate active = template("ACTIVE");
        when(mapper.selectById(3L)).thenReturn(draft, draft, active);
        when(mapper.activate(3L)).thenReturn(1);
        ReportTemplateService service = service(mapper);

        var response = service.activate(7L, 3L);

        assertEquals("ACTIVE", response.status());
        InOrder order = inOrder(mapper);
        order.verify(mapper).disableOtherActive("REVIEW", 3L);
        order.verify(mapper).activate(3L);
    }

    @Test
    void shouldNotReactivateDisabledTemplate() {
        ReportTemplateMapper mapper = mock(ReportTemplateMapper.class);
        when(mapper.selectById(3L)).thenReturn(template("DISABLED"));
        ReportTemplateService service = service(mapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.activate(7L, 3L));

        assertEquals("REPORT_TEMPLATE_STATUS_CONFLICT", exception.getErrorCode());
    }

    @Test
    void shouldNotDisableDraftTemplate() {
        ReportTemplateMapper mapper = mock(ReportTemplateMapper.class);
        when(mapper.selectById(3L)).thenReturn(template("DRAFT"));
        ReportTemplateService service = service(mapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.disable(7L, 3L));

        assertEquals("REPORT_TEMPLATE_STATUS_CONFLICT", exception.getErrorCode());
    }

    private ReportTemplateService service(ReportTemplateMapper mapper) {
        return new ReportTemplateService(mapper, mock(AuditService.class), new ObjectMapper(),
                new ReportContentValidator());
    }

    private ReportTemplate template(String status) {
        ReportTemplate template = new ReportTemplate();
        template.setTemplateId(3L);
        template.setTemplateCode("REVIEW");
        template.setTemplateName("审查报告");
        template.setTemplateType("REVIEW_REPORT");
        template.setTemplateContentJson("""
                {"sections":[{"code":"FACTS","title":"案件事实"}]}
                """);
        template.setVersionNo(2);
        template.setSchemaVersion("1.0");
        template.setStatus(status);
        template.setCreatedBy(7L);
        return template;
    }
}
