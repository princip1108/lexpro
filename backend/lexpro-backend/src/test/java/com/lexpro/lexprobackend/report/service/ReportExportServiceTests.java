package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.report.config.ReportExportProperties;
import com.lexpro.lexprobackend.report.domain.ReportExportData;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportExportServiceTests {

    @Test
    void shouldCreateEditableDocx() throws Exception {
        Fixture fixture = new Fixture();

        ExportedReport exported = fixture.service.export(7L, 9L, 40L, "docx");

        assertEquals("PK", new String(exported.content(), 0, 2, StandardCharsets.US_ASCII));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                exported.contentType());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported.content()))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText()).reduce("", String::concat);
            assertTrue(text.contains("案件事实"));
            assertTrue(text.contains("草稿 - DRAFT"));
        }
        verify(fixture.access).requireRead(9L, 7L);
    }

    @Test
    void shouldCreateReadableChinesePdfWithConfiguredFont() throws Exception {
        Fixture fixture = new Fixture();
        fixture.properties.setPdfFontPath("C:\\Windows\\Fonts\\simhei.ttf");

        ExportedReport exported = fixture.service.export(7L, 9L, 40L, "PDF");

        assertEquals("%PDF-", new String(exported.content(), 0, 5, StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(exported.content())) {
            assertTrue(document.getNumberOfPages() >= 1);
            assertTrue(new PDFTextStripper().getText(document).contains("案件事实"));
        }
    }

    @Test
    void shouldRequireConfiguredFontForPdf() {
        Fixture fixture = new Fixture();

        ApiException exception = assertThrows(ApiException.class,
                () -> fixture.service.export(7L, 9L, 40L, "PDF"));

        assertEquals("REPORT_PDF_FONT_NOT_CONFIGURED", exception.getErrorCode());
    }

    private static final class Fixture {

        private final CaseReportMapper mapper = mock(CaseReportMapper.class);
        private final ReportTemplateMapper templateMapper = mock(ReportTemplateMapper.class);
        private final CaseAccessService access = mock(CaseAccessService.class);
        private final ReportExportProperties properties = new ReportExportProperties();
        private final ReportExportService service;

        private Fixture() {
            when(mapper.selectExportData(9L, 40L)).thenReturn(new ReportExportData(
                    40L, 9L, 3L, "REVIEW_REPORT", "DRAFT", "审查报告",
                    """
                            {"sections":[{"code":"FACTS","title":"案件事实","content":"这是案件事实。"}]}
                            """,
                    2, "案-2026-001", "测试案件"));
            ReportTemplate template = new ReportTemplate();
            template.setTemplateContentJson("""
                    {"sections":[{"code":"FACTS","title":"案件事实"}]}
                    """);
            when(templateMapper.selectById(3L)).thenReturn(template);
            service = new ReportExportService(mapper, templateMapper, access, new ReportContentValidator(),
                    properties, mock(AuditService.class), new ObjectMapper());
        }
    }
}
