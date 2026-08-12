package com.lexpro.lexprobackend.report.service;

import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.web.dto.ReportEvidenceRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTypicalCaseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReportSourceService {

    private final CaseReportMapper mapper;

    public ReportSourceService(CaseReportMapper mapper) {
        this.mapper = mapper;
    }

    public List<ReportSourceMaterial> load(long caseId, Long cardFillTaskId,
                                           List<ReportEvidenceRequest> evidence,
                                           List<Long> legalElementResultIds,
                                           List<ReportTypicalCaseRequest> typicalCases) {
        List<ReportSourceMaterial> sources = new ArrayList<>();
        if (cardFillTaskId != null) {
            sources.add(requireReady(mapper.selectCardSource(caseId, cardFillTaskId),
                    "CASE_REPORT_CARD_UNAVAILABLE"));
        }
        for (ReportEvidenceRequest reference : evidence) {
            sources.add(requireReady(mapper.selectEvidenceSource(caseId, reference.dossierId()),
                    "CASE_REPORT_EVIDENCE_UNAVAILABLE"));
        }
        for (Long elementResultId : legalElementResultIds) {
            sources.add(requireReady(mapper.selectLegalElementSource(caseId, elementResultId),
                    "CASE_REPORT_ELEMENT_UNAVAILABLE"));
        }
        for (ReportTypicalCaseRequest reference : typicalCases) {
            if (reference.recommendationItemId() != null
                    && mapper.countRecommendationItem(caseId, reference.recommendationItemId(),
                    reference.typicalCaseId()) == 0) {
                throw invalid("The recommendation item does not match this case and typical case",
                        "CASE_REPORT_RECOMMENDATION_MISMATCH");
            }
            sources.add(requireReady(mapper.selectTypicalCaseSource(reference.typicalCaseId()),
                    "CASE_REPORT_TYPICAL_CASE_UNAVAILABLE"));
        }
        if (sources.isEmpty()) {
            throw invalid("At least one report source is required", "CASE_REPORT_SOURCES_EMPTY");
        }
        return List.copyOf(sources);
    }

    private ReportSourceMaterial requireReady(ReportSourceMaterial source, String errorCode) {
        if (source == null) {
            throw invalid("A selected report source does not exist or is outside the case", errorCode);
        }
        if (!Boolean.TRUE.equals(source.ready()) || source.content() == null || source.content().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "Report source unavailable", errorCode,
                    "A selected report source is not ready for generation");
        }
        return source;
    }

    private ApiException invalid(String detail, String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid report source", code, detail);
    }
}
