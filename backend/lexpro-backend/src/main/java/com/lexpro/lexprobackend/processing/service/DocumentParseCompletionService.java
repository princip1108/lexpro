package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import com.lexpro.lexprobackend.processing.mapper.DocumentParseMapper;
import com.lexpro.lexprobackend.processing.parser.ParsedDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DocumentParseCompletionService {

    private final DocumentParseMapper mapper;
    private final AuditService auditService;

    public DocumentParseCompletionService(DocumentParseMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional
    public void succeed(DocumentParseJob job, ParsedDocument parsed, int durationMs) {
        if (mapper.markSuccess(job.docId(), parsed.rawText(), parsed.parsedTextJson(),
                parsed.parserVersion(), durationMs) == 1) {
            auditService.record(new AuditEvent(job.requestedBy(), job.caseId(), "DOCUMENT_PARSE_SUCCEEDED",
                    "DOCUMENT_PARSE_RESULT", String.valueOf(job.docId()), AuditResult.SUCCESS,
                    Map.of("dossierId", job.dossierId(), "versionNo", job.versionNo(),
                            "durationMs", durationMs, "parserVersion", parsed.parserVersion())), job.requestId());
        }
    }

    @Transactional
    public void fail(DocumentParseJob job, String errorCode, int durationMs) {
        if (mapper.markFailed(job.docId(), errorCode, durationMs) == 1) {
            auditService.record(new AuditEvent(job.requestedBy(), job.caseId(), "DOCUMENT_PARSE_FAILED",
                    "DOCUMENT_PARSE_RESULT", String.valueOf(job.docId()), AuditResult.FAILED,
                    Map.of("dossierId", job.dossierId(), "versionNo", job.versionNo(),
                            "durationMs", durationMs, "errorCode", errorCode)), job.requestId());
        }
    }
}
