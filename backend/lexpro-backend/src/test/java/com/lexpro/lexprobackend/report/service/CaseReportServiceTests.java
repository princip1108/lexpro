package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.domain.ReportJobRecord;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.web.dto.ReportEvidenceRequest;
import com.lexpro.lexprobackend.report.web.dto.StartReportRequest;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseReportServiceTests {

    @Test
    void shouldStartVersionedReportWithNormalizedEvidenceReference() {
        CaseReportMapper mapper = mock(CaseReportMapper.class);
        ReportTemplateMapper templateMapper = mock(ReportTemplateMapper.class);
        ReportSourceService sourceService = mock(ReportSourceService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(templateMapper.selectById(3L)).thenReturn(template());
        when(sourceService.load(anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of(new ReportSourceMaterial("EVIDENCE", 12L, "document", "text", true)));
        when(mapper.lockCase(9L)).thenReturn(9L);
        when(mapper.selectNextVersion(9L, "REVIEW_REPORT")).thenReturn(2);
        doAnswer(invocation -> {
            invocation.<com.lexpro.lexprobackend.report.domain.CaseReport>getArgument(0).setReportId(40L);
            return 1;
        }).when(mapper).insert(any());
        when(mapper.selectJob(anyLong(), anyString())).thenAnswer(invocation ->
                new ReportJobRecord(invocation.getArgument(1), 40L, "REVIEW_REPORT", "PROCESSING", null,
                        OffsetDateTime.now(), null));
        CaseReportService service = service(mapper, templateMapper, sourceService, publisher);

        var response = service.start(7L, 9L, new StartReportRequest("REVIEW_REPORT", "审查报告", 3L,
                null, List.of(new ReportEvidenceRequest(12L, 1)), List.of(), List.of()), "http-1");

        assertEquals(40L, response.reportId());
        assertEquals("PROCESSING", response.status());
        verify(mapper).insertEvidenceReference(40L, 9L, 12L, 1);
        verify(publisher).publishEvent(any(ReportRequestedEvent.class));
    }

    @Test
    void shouldRejectDuplicateReferencesBeforeGeneration() {
        CaseReportMapper mapper = mock(CaseReportMapper.class);
        ReportTemplateMapper templateMapper = mock(ReportTemplateMapper.class);
        when(templateMapper.selectById(3L)).thenReturn(template());
        CaseReportService service = service(mapper, templateMapper, mock(ReportSourceService.class),
                mock(ApplicationEventPublisher.class));

        ApiException exception = assertThrows(ApiException.class, () -> service.start(7L, 9L,
                new StartReportRequest("REVIEW_REPORT", "审查报告", 3L, null,
                        List.of(new ReportEvidenceRequest(12L, 1), new ReportEvidenceRequest(12L, 2)),
                        List.of(), List.of()), "http-1"));

        assertEquals("CASE_REPORT_REFERENCES_DUPLICATE", exception.getErrorCode());
    }

    private CaseReportService service(CaseReportMapper mapper, ReportTemplateMapper templateMapper,
                                      ReportSourceService sourceService, ApplicationEventPublisher publisher) {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setAllowExternalCaseData(true);
        properties.setModel("deepseek-v4-flash");
        properties.setMaxInputChars(60_000);
        return new CaseReportService(mapper, templateMapper, sourceService, mock(CaseAccessService.class),
                properties, publisher, mock(AuditService.class), new ObjectMapper(), new ReportContentValidator());
    }

    private ReportTemplate template() {
        ReportTemplate template = new ReportTemplate();
        template.setTemplateId(3L);
        template.setTemplateType("REVIEW_REPORT");
        template.setTemplateContentJson("""
                {"sections":[{"code":"FACTS","title":"案件事实","instructions":"概述事实"}]}
                """);
        template.setSchemaVersion("1.0");
        template.setStatus("ACTIVE");
        return template;
    }
}
