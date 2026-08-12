package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseResult;
import com.lexpro.lexprobackend.processing.domain.ParseSourceFile;
import com.lexpro.lexprobackend.processing.mapper.DocumentParseMapper;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseDetailResponse;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartDocumentParseRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class DocumentParseService {

    private static final int MAX_PARAMETERS_JSON_LENGTH = 16_384;
    private final DocumentParseMapper mapper;
    private final CaseAccessService caseAccessService;
    private final DocumentProcessingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DocumentParseService(DocumentParseMapper mapper, CaseAccessService caseAccessService,
                                DocumentProcessingProperties properties, ApplicationEventPublisher eventPublisher,
                                AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.caseAccessService = caseAccessService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentParseSummaryResponse start(long userId, long caseId, long dossierId,
                                              StartDocumentParseRequest request, String requestId) {
        caseAccessService.requireEdit(caseId, userId);
        ParseSourceFile source = mapper.lockActiveSource(caseId, dossierId);
        if (source == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Dossier file not found", "DOSSIER_FILE_NOT_FOUND",
                    "The requested active dossier file does not exist");
        }
        DocumentParseResult current = mapper.selectCurrent(caseId, dossierId);
        if (current != null && "PROCESSING".equals(current.getParseStatus())) {
            OffsetDateTime staleBefore = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getStaleAfter());
            if (current.getCreatedAt() == null || current.getCreatedAt().isAfter(staleBefore)) {
                throw new ApiException(HttpStatus.CONFLICT, "Parsing already in progress", "PARSE_IN_PROGRESS",
                        "The current file version is already being parsed");
            }
            mapper.markStaleFailed(current.getDocId(), current.getCreatedAt());
        }

        String parametersJson = serializeParameters(request == null ? null : request.parserParameters());
        mapper.clearCurrent(dossierId);
        DocumentParseResult result = new DocumentParseResult();
        result.setDossierId(dossierId);
        result.setCaseId(caseId);
        result.setVersionNo(mapper.selectNextVersion(dossierId));
        result.setRequestedBy(userId);
        result.setRequestId(requestId);
        result.setParserParametersJson(parametersJson);
        mapper.insert(result);
        eventPublisher.publishEvent(new DocumentParseRequestedEvent(result.getDocId()));
        auditService.record(new AuditEvent(userId, caseId, "DOCUMENT_PARSE_STARTED", "DOCUMENT_PARSE_RESULT",
                String.valueOf(result.getDocId()), AuditResult.SUCCESS,
                Map.of("dossierId", dossierId, "versionNo", result.getVersionNo())));
        return DocumentParseSummaryResponse.from(requireDetail(caseId, result.getDocId()));
    }

    @Transactional(readOnly = true)
    public List<DocumentParseSummaryResponse> history(long userId, long caseId, long dossierId) {
        caseAccessService.requireRead(caseId, userId);
        if (mapper.selectSource(caseId, dossierId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Dossier file not found", "DOSSIER_FILE_NOT_FOUND",
                    "The requested dossier file does not exist");
        }
        return mapper.selectHistory(caseId, dossierId).stream().map(DocumentParseSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentParseDetailResponse detail(long userId, long caseId, long docId) {
        caseAccessService.requireRead(caseId, userId);
        return DocumentParseDetailResponse.from(requireDetail(caseId, docId), objectMapper);
    }

    private DocumentParseResult requireDetail(long caseId, long docId) {
        DocumentParseResult result = mapper.selectDetail(caseId, docId);
        if (result == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Parse result not found", "PARSE_RESULT_NOT_FOUND",
                    "The requested parse result does not exist");
        }
        return result;
    }

    private String serializeParameters(Map<String, Object> parameters) {
        try {
            String json = objectMapper.writeValueAsString(parameters == null ? Map.of() : parameters);
            if (json.length() > MAX_PARAMETERS_JSON_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Parser parameters too large",
                        "PARSER_PARAMETERS_TOO_LARGE", "Parser parameters exceed the allowed size");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid parser parameters", "PARSER_PARAMETERS_INVALID",
                    "Parser parameters must be JSON serializable");
        }
    }
}
