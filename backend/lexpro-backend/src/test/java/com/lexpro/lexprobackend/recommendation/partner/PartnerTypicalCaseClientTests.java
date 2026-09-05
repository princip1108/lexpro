package com.lexpro.lexprobackend.recommendation.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PartnerTypicalCaseClientTests {

    @Test
    void shouldSendExplicitAnalysisIdToSearchAndParseSnakeCaseResponse() {
        RestClient.Builder analyzeBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        RestClient.Builder searchBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        MockRestServiceServer searchServer = MockRestServiceServer.bindTo(searchBuilder).build();
        PartnerTypicalCaseClient client = new PartnerTypicalCaseClient(
                analyzeBuilder.build(), searchBuilder.build(), new ObjectMapper());
        searchServer.expect(once(), requestTo("http://127.0.0.1:8000/api/v1/typical-cases/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"analysis_id":"analysis-1","filters":{},"top_k":1}
                        """, false))
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{"retrieval_id":"retrieval-1",
                        "analysis_id":"analysis-1","candidate_count":0,
                        "ranking_rule":"fact_similarity + 0.3 * issue_score","score_weights":{},
                        "candidates":[],"timing":{},"pipeline_timing":{}}}
                        """, MediaType.APPLICATION_JSON));

        PartnerTypicalCaseContract.SearchEnvelope response = client.search(
                new PartnerTypicalCaseContract.SearchRequest("analysis-1",
                        new PartnerTypicalCaseContract.Filters(
                                null, null, null, null, null, null, null, null, null, null, null), 1));

        assertEquals("retrieval-1", response.data().retrievalId());
        searchServer.verify();
    }

    @Test
    void shouldClassifyMissingProviderAnalysisWithoutExposingProviderBody() {
        RestClient.Builder analyzeBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        RestClient.Builder searchBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        MockRestServiceServer searchServer = MockRestServiceServer.bindTo(searchBuilder).build();
        PartnerTypicalCaseClient client = new PartnerTypicalCaseClient(
                analyzeBuilder.build(), searchBuilder.build(), new ObjectMapper());
        searchServer.expect(once(), requestTo("http://127.0.0.1:8000/api/v1/typical-cases/search"))
                .andRespond(withResourceNotFound());

        PartnerTypicalCaseClientException exception = assertThrows(PartnerTypicalCaseClientException.class,
                () -> client.search(new PartnerTypicalCaseContract.SearchRequest(
                        "expired", new PartnerTypicalCaseContract.Filters(
                        null, null, null, null, null, null, null, null, null, null, null), 1)));

        assertEquals(PartnerTypicalCaseClientException.Kind.ANALYSIS_EXPIRED, exception.getKind());
        searchServer.verify();
    }
}
