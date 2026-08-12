package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.domain.CaseReport;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import com.lexpro.lexprobackend.report.web.dto.ReportTransitionRequest;
import com.lexpro.lexprobackend.report.web.dto.ReturnReportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseReportTransitionServiceTests {

    @Test
    void shouldSubmitCurrentDraftUsingEditAccess() {
        Fixture fixture = new Fixture();
        fixture.stubTransition("DRAFT", "REVIEWING");
        when(fixture.mapper.submitReview(9L, 40L, 2)).thenReturn(1);

        var response = fixture.service.submitReview(7L, 9L, 40L, new ReportTransitionRequest(2));

        assertEquals("REVIEWING", response.report().reportStatus());
        verify(fixture.access).requireEdit(9L, 7L);
        verify(fixture.mapper).submitReview(9L, 40L, 2);
    }

    @Test
    void shouldRecordReasonWhenReviewIsReturned() {
        Fixture fixture = new Fixture();
        fixture.stubTransition("REVIEWING", "DRAFT");
        when(fixture.mapper.returnToDraft(9L, 40L, 3)).thenReturn(1);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);

        fixture.service.returnToDraft(7L, 9L, 40L, new ReturnReportRequest(3, "  补充证据说明  "));

        verify(fixture.access).requireEdit(9L, 7L);
        verify(fixture.audit).record(event.capture());
        assertEquals("补充证据说明", event.getValue().detail().get("reason"));
    }

    @Test
    void shouldFinalizeReviewUsingManageAccess() {
        Fixture fixture = new Fixture();
        fixture.stubTransition("REVIEWING", "FINAL");
        when(fixture.mapper.finalizeReport(9L, 40L, 7L, 3)).thenReturn(1);

        var response = fixture.service.finalizeReport(7L, 9L, 40L, new ReportTransitionRequest(3));

        assertEquals("FINAL", response.report().reportStatus());
        verify(fixture.access).requireManage(9L, 7L);
        verify(fixture.mapper).finalizeReport(9L, 40L, 7L, 3);
    }

    @Test
    void shouldRejectStaleLockVersionWithoutAudit() {
        Fixture fixture = new Fixture();
        when(fixture.mapper.selectDetail(9L, 40L)).thenReturn(fixture.report("DRAFT"));
        when(fixture.mapper.submitReview(9L, 40L, 1)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> fixture.service.submitReview(7L, 9L, 40L, new ReportTransitionRequest(1)));

        assertEquals("CASE_REPORT_TRANSITION_CONFLICT", exception.getErrorCode());
        verify(fixture.audit, never()).record(any(AuditEvent.class));
    }

    private static final class Fixture {

        private final CaseReportMapper mapper = mock(CaseReportMapper.class);
        private final ReportTemplateMapper templateMapper = mock(ReportTemplateMapper.class);
        private final CaseAccessService access = mock(CaseAccessService.class);
        private final AuditService audit = mock(AuditService.class);
        private final CaseReportService service;

        private Fixture() {
            when(templateMapper.selectById(3L)).thenReturn(template());
            service = new CaseReportService(mapper, templateMapper, mock(ReportSourceService.class), access,
                    new AiProcessingProperties(), mock(org.springframework.context.ApplicationEventPublisher.class),
                    audit, new ObjectMapper(), new ReportContentValidator());
        }

        private void stubTransition(String before, String after) {
            when(mapper.selectDetail(9L, 40L)).thenReturn(report(before), report(after));
        }

        private CaseReport report(String status) {
            CaseReport report = new CaseReport();
            report.setReportId(40L);
            report.setCaseId(9L);
            report.setTemplateId(3L);
            report.setVersionNo(2);
            report.setReportType("REVIEW_REPORT");
            report.setReportStatus(status);
            report.setReportTitle("审查报告");
            report.setReportContentJson("""
                    {"sections":[{"code":"FACTS","title":"案件事实","content":"事实内容"}]}
                    """);
            report.setCurrent(true);
            report.setLockVersion("DRAFT".equals(status) ? 2 : 3);
            return report;
        }

        private ReportTemplate template() {
            ReportTemplate template = new ReportTemplate();
            template.setTemplateId(3L);
            template.setTemplateContentJson("""
                    {"sections":[{"code":"FACTS","title":"案件事实"}]}
                    """);
            return template;
        }
    }
}
