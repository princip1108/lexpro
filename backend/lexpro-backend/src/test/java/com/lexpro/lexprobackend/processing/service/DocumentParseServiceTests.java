package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseResult;
import com.lexpro.lexprobackend.processing.domain.ParseSourceFile;
import com.lexpro.lexprobackend.processing.mapper.DocumentParseMapper;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartDocumentParseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentParseServiceTests {

    @Test
    void shouldCreateNextCurrentVersionAndPublishAfterTransaction() {
        DocumentParseMapper mapper = mock(DocumentParseMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.lockActiveSource(9L, 5L)).thenReturn(source());
        when(mapper.selectNextVersion(5L)).thenReturn(3);
        when(mapper.insert(any(DocumentParseResult.class))).thenAnswer(invocation -> {
            DocumentParseResult value = invocation.getArgument(0);
            value.setDocId(12L);
            return 1;
        });
        when(mapper.selectDetail(9L, 12L)).thenReturn(result(12L, 3, "PROCESSING"));
        DocumentParseService service = service(mapper, accessService, publisher);

        DocumentParseSummaryResponse response = service.start(7L, 9L, 5L,
                new StartDocumentParseRequest(Map.of("language", "zh-CN")), "request-123");

        assertEquals(12L, response.docId());
        assertEquals(3, response.versionNo());
        verify(accessService).requireEdit(9L, 7L);
        verify(mapper).clearCurrent(5L);
        verify(publisher).publishEvent(new DocumentParseRequestedEvent(12L));
    }

    @Test
    void shouldRejectSecondRequestWhileCurrentJobIsFresh() {
        DocumentParseMapper mapper = mock(DocumentParseMapper.class);
        when(mapper.lockActiveSource(9L, 5L)).thenReturn(source());
        DocumentParseResult current = result(11L, 2, "PROCESSING");
        current.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(mapper.selectCurrent(9L, 5L)).thenReturn(current);
        DocumentParseService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.start(7L, 9L, 5L, null, "request-123"));

        assertEquals("PARSE_IN_PROGRESS", exception.getErrorCode());
        verify(mapper, never()).clearCurrent(5L);
    }

    @Test
    void shouldFailStaleJobAndCreateRetryVersion() {
        DocumentParseMapper mapper = mock(DocumentParseMapper.class);
        when(mapper.lockActiveSource(9L, 5L)).thenReturn(source());
        DocumentParseResult stale = result(11L, 2, "PROCESSING");
        stale.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(mapper.selectCurrent(9L, 5L)).thenReturn(stale);
        when(mapper.selectNextVersion(5L)).thenReturn(3);
        when(mapper.insert(any(DocumentParseResult.class))).thenAnswer(invocation -> {
            DocumentParseResult value = invocation.getArgument(0);
            value.setDocId(12L);
            return 1;
        });
        when(mapper.selectDetail(9L, 12L)).thenReturn(result(12L, 3, "PROCESSING"));
        DocumentParseService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        service.start(7L, 9L, 5L, null, "request-123");

        verify(mapper).markStaleFailed(11L, stale.getCreatedAt());
        verify(mapper).clearCurrent(5L);
    }

    private DocumentParseService service(DocumentParseMapper mapper, CaseAccessService accessService,
                                         ApplicationEventPublisher publisher) {
        return new DocumentParseService(mapper, accessService, new DocumentProcessingProperties(), publisher,
                mock(AuditService.class), new ObjectMapper());
    }

    private ParseSourceFile source() {
        return new ParseSourceFile(5L, 9L, "source.txt", "text/plain",
                "cases/9/private.txt", "ACTIVE");
    }

    private DocumentParseResult result(long docId, int version, String status) {
        DocumentParseResult result = new DocumentParseResult();
        result.setDocId(docId);
        result.setDossierId(5L);
        result.setCaseId(9L);
        result.setVersionNo(version);
        result.setParseStatus(status);
        result.setRequestedBy(7L);
        result.setRequestId("request-123");
        result.setParserParametersJson("{}");
        result.setCurrent(true);
        return result;
    }
}
