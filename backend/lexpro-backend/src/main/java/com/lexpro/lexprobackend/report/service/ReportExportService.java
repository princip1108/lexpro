package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.report.config.ReportExportProperties;
import com.lexpro.lexprobackend.report.domain.ReportExportData;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.validation.ReportContentValidationException;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ReportExportService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> EXPORTABLE_STATUSES = Set.of("DRAFT", "REVIEWING", "FINAL");

    private final CaseReportMapper mapper;
    private final ReportTemplateMapper templateMapper;
    private final CaseAccessService caseAccessService;
    private final ReportContentValidator contentValidator;
    private final ReportExportProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ReportExportService(CaseReportMapper mapper, ReportTemplateMapper templateMapper,
                               CaseAccessService caseAccessService, ReportContentValidator contentValidator,
                               ReportExportProperties properties, AuditService auditService,
                               ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.templateMapper = templateMapper;
        this.caseAccessService = caseAccessService;
        this.contentValidator = contentValidator;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportedReport export(long userId, long caseId, long reportId, String format) {
        caseAccessService.requireRead(caseId, userId);
        String checkedFormat = normalizeFormat(format);
        ReportExportData report = mapper.selectExportData(caseId, reportId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Case report not found", "CASE_REPORT_NOT_FOUND",
                    "The requested case report does not exist");
        }
        if (!EXPORTABLE_STATUSES.contains(report.reportStatus()) || report.reportContentJson() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Report cannot be exported", "CASE_REPORT_NOT_EXPORTABLE",
                    "Only generated draft, reviewing or final reports can be exported");
        }
        ReportTemplate template = templateMapper.selectById(report.templateId());
        List<ReportContentValidator.ReportSection> sections = validateContent(report, template);
        byte[] content = "DOCX".equals(checkedFormat)
                ? createDocx(report, sections) : createPdf(report, sections);
        String extension = checkedFormat.toLowerCase(Locale.ROOT);
        String contentType = "DOCX".equals(checkedFormat) ? DOCX_CONTENT_TYPE : "application/pdf";
        String fileName = fileName(report, extension);
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_EXPORTED", "CASE_REPORT",
                String.valueOf(reportId), AuditResult.SUCCESS,
                Map.of("format", checkedFormat, "reportStatus", report.reportStatus())));
        return new ExportedReport(content, contentType, fileName);
    }

    private List<ReportContentValidator.ReportSection> validateContent(ReportExportData report,
                                                                        ReportTemplate template) {
        if (template == null) {
            throw exportConflict("The report template no longer exists");
        }
        try {
            JsonNode content = objectMapper.readTree(report.reportContentJson());
            JsonNode templateContent = objectMapper.readTree(template.getTemplateContentJson());
            return contentValidator.validateReport(content, templateContent);
        } catch (ReportContentValidationException exception) {
            throw exportConflict(exception.getMessage());
        } catch (IOException exception) {
            throw exportConflict("Stored report content is invalid");
        }
    }

    private byte[] createDocx(ReportExportData report, List<ReportContentValidator.ReportSection> sections) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            addRun(title, report.reportTitle(), 18, true);
            XWPFParagraph caseInfo = document.createParagraph();
            caseInfo.setAlignment(ParagraphAlignment.CENTER);
            addRun(caseInfo, caseLine(report), 10, false);
            if (!"FINAL".equals(report.reportStatus())) {
                XWPFParagraph draft = document.createParagraph();
                draft.setAlignment(ParagraphAlignment.CENTER);
                addRun(draft, "草稿 - " + report.reportStatus(), 11, true);
            }
            for (ReportContentValidator.ReportSection section : sections) {
                XWPFParagraph heading = document.createParagraph();
                heading.setSpacingBefore(240);
                addRun(heading, section.title(), 14, true);
                for (String paragraphText : splitParagraphs(section.content())) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setFirstLineIndent(420);
                    paragraph.setSpacingBetween(1.5);
                    addRun(paragraph, paragraphText, 11, false);
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw exportFailure();
        }
    }

    private byte[] createPdf(ReportExportData report, List<ReportContentValidator.ReportSection> sections) {
        Path fontPath = requirePdfFont();
        try (PDDocument document = new PDDocument(); InputStream fontInput = Files.newInputStream(fontPath);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontInput, true);
            try (PdfLayout layout = new PdfLayout(document, font)) {
                layout.center(report.reportTitle(), 18, 28);
                layout.center(caseLine(report), 10, 18);
                if (!"FINAL".equals(report.reportStatus())) {
                    layout.center("草稿 - " + report.reportStatus(), 11, 22);
                }
                for (ReportContentValidator.ReportSection section : sections) {
                    layout.paragraph(section.title(), 14, 24, true);
                    for (String text : splitParagraphs(section.content())) {
                        layout.paragraph(text, 11, 19, false);
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            throw exportFailure();
        }
    }

    private void addRun(XWPFParagraph paragraph, String text, int fontSize, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("SimSun");
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setText(text);
    }

    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("\\R")) {
            String text = paragraph.trim();
            if (!text.isEmpty()) {
                paragraphs.add(text);
            }
        }
        return paragraphs.isEmpty() ? List.of(content) : paragraphs;
    }

    private Path requirePdfFont() {
        String configured = properties.getPdfFontPath();
        if (configured == null || configured.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PDF font unavailable",
                    "REPORT_PDF_FONT_NOT_CONFIGURED", "Configure LEXPRO_REPORT_PDF_FONT_PATH before PDF export");
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PDF font unavailable",
                    "REPORT_PDF_FONT_UNAVAILABLE", "The configured PDF font is unavailable");
        }
        return path;
    }

    private String caseLine(ReportExportData report) {
        String number = report.caseNo() == null || report.caseNo().isBlank()
                ? "案件 " + report.caseId() : report.caseNo();
        return report.caseName() + "  " + number + "  第" + report.versionNo() + "版";
    }

    private String fileName(ReportExportData report, String extension) {
        String base = (report.caseNo() == null || report.caseNo().isBlank()
                ? "case-" + report.caseId() : report.caseNo()) + "-" + report.reportType()
                + "-v" + report.versionNo();
        return base.replaceAll("[^\\p{L}\\p{N}._-]", "_") + '.' + extension;
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DOCX", "PDF").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid export format", "REPORT_EXPORT_FORMAT_INVALID",
                    "Report export format must be DOCX or PDF");
        }
        return normalized;
    }

    private ApiException exportConflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Report cannot be exported",
                "CASE_REPORT_CONTENT_INVALID", detail);
    }

    private ApiException exportFailure() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Report export failed", "REPORT_EXPORT_FAILED",
                "The report document could not be generated");
    }

    private static final class PdfLayout implements AutoCloseable {

        private static final float MARGIN = 56;
        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PdfLayout(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void center(String text, float size, float lineHeight) throws IOException {
            ensureSpace(lineHeight);
            float width = textWidth(text, size);
            writeLine(text, size, Math.max(MARGIN, (PDRectangle.A4.getWidth() - width) / 2));
            y -= lineHeight;
        }

        private void paragraph(String text, float size, float lineHeight, boolean heading) throws IOException {
            if (heading) {
                y -= 6;
            }
            List<String> lines = wrap(text, size, PDRectangle.A4.getWidth() - MARGIN * 2);
            for (int index = 0; index < lines.size(); index++) {
                ensureSpace(lineHeight);
                float indent = !heading && index == 0 ? size * 2 : 0;
                writeLine(lines.get(index), size, MARGIN + indent);
                y -= lineHeight;
            }
            y -= heading ? 2 : 7;
        }

        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (!line.isEmpty() && textWidth(candidate, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private float textWidth(String text, float size) throws IOException {
            return font.getStringWidth(text) / 1000f * size;
        }

        private void writeLine(String text, float size, float x) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }

        private void ensureSpace(float lineHeight) throws IOException {
            if (y - lineHeight < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
