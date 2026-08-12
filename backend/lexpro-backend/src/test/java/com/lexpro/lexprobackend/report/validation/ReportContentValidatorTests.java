package com.lexpro.lexprobackend.report.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportContentValidatorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportContentValidator validator = new ReportContentValidator();

    @Test
    void shouldAcceptMatchingTemplateAndReportSections() throws Exception {
        var template = objectMapper.readTree("""
                {"sections":[
                  {"code":"FACTS","title":"案件事实","instructions":"概述事实"},
                  {"code":"OPINION","title":"审查意见"}
                ]}
                """);
        var report = objectMapper.readTree("""
                {"sections":[
                  {"code":"FACTS","title":"案件事实","content":"事实内容"},
                  {"code":"OPINION","title":"审查意见","content":"意见内容"}
                ]}
                """);

        var sections = validator.validateReport(report, template);

        assertEquals(2, sections.size());
        assertEquals("事实内容", sections.getFirst().content());
    }

    @Test
    void shouldRejectChangedSectionOrder() throws Exception {
        var template = objectMapper.readTree("""
                {"sections":[
                  {"code":"FACTS","title":"案件事实"},
                  {"code":"OPINION","title":"审查意见"}
                ]}
                """);
        var report = objectMapper.readTree("""
                {"sections":[
                  {"code":"OPINION","title":"审查意见","content":"意见内容"},
                  {"code":"FACTS","title":"案件事实","content":"事实内容"}
                ]}
                """);

        assertThrows(ReportContentValidationException.class,
                () -> validator.validateReport(report, template));
    }
}
