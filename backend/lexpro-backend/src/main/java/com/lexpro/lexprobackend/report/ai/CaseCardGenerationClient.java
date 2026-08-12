package com.lexpro.lexprobackend.report.ai;

import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;

import java.util.List;

public interface CaseCardGenerationClient {

    CaseCardGenerationOutput generate(List<CaseCardSourceMaterial> sources, String requestId);
}
