package com.lexpro.lexprobackend.report.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ReportContentValidator {

    private static final int MAX_SECTIONS = 50;
    private static final int MAX_CONTENT_CHARS = 2_000_000;

    public List<TemplateSection> validateTemplate(JsonNode content) {
        JsonNode sections = requireSections(content);
        Set<String> codes = new HashSet<>();
        List<TemplateSection> result = new ArrayList<>();
        for (JsonNode section : sections) {
            String code = requiredText(section, "code", 100);
            String title = requiredText(section, "title", 255);
            String instructions = optionalText(section, "instructions", 5_000);
            if (!code.matches("[A-Z][A-Z0-9_]{0,99}") || !codes.add(code)) {
                throw invalid("Template section codes must be unique upper snake case");
            }
            result.add(new TemplateSection(code, title, instructions));
        }
        return List.copyOf(result);
    }

    public List<ReportSection> validateReport(JsonNode content, JsonNode templateContent) {
        List<TemplateSection> templateSections = validateTemplate(templateContent);
        JsonNode sections = requireSections(content);
        if (sections.size() != templateSections.size() || content.toString().length() > MAX_CONTENT_CHARS) {
            throw invalid("Report sections must match the template");
        }
        List<ReportSection> result = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            JsonNode section = sections.get(index);
            TemplateSection expected = templateSections.get(index);
            String code = requiredText(section, "code", 100);
            String title = requiredText(section, "title", 255);
            String text = requiredText(section, "content", 100_000);
            if (!expected.code().equals(code) || !expected.title().equals(title)) {
                throw invalid("Report section order, code and title must match the template");
            }
            result.add(new ReportSection(code, title, text));
        }
        return List.copyOf(result);
    }

    private JsonNode requireSections(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw invalid("Content must be a JSON object");
        }
        JsonNode sections = content.path("sections");
        if (!sections.isArray() || sections.isEmpty() || sections.size() > MAX_SECTIONS) {
            throw invalid("Content must contain one to fifty sections");
        }
        return sections;
    }

    private String requiredText(JsonNode node, String name, int maxLength) {
        if (!node.isObject() || !node.path(name).isTextual()) {
            throw invalid(name + " must be text");
        }
        String value = node.path(name).textValue().trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid(name + " is blank or too long");
        }
        return value;
    }

    private String optionalText(JsonNode node, String name, int maxLength) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > maxLength) {
            throw invalid(name + " must be bounded text");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    private ReportContentValidationException invalid(String message) {
        return new ReportContentValidationException(message);
    }

    public record TemplateSection(String code, String title, String instructions) {
    }

    public record ReportSection(String code, String title, String content) {
    }
}
