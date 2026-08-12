package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;

import java.util.List;

public interface ReportGenerationClient {

    ReportGenerationOutput generate(String reportType, JsonNode templateContent,
                                    List<ReportSourceMaterial> sources, String requestId);
}
