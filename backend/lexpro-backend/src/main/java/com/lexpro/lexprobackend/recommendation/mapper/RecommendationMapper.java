package com.lexpro.lexprobackend.recommendation.mapper;

import com.lexpro.lexprobackend.recommendation.client.RetrievalContract;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationItemRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationWrite;
import com.lexpro.lexprobackend.recommendation.domain.TypicalCaseRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RecommendationMapper {

    @Select("""
            INSERT INTO lexpro.typical_case (
                external_case_id, title, case_cause, case_cause_full_json, case_type,
                country, court, court_level, doc_type, dispute_focus_json, judgment_date,
                procedure, applicable_law_json, case_level, embedding,
                embedding_model_name, embedding_model_version, embedded_at
            ) VALUES (
                #{item.externalCaseId}, #{item.title}, #{item.caseCause}, CAST(#{caseCausesJson} AS jsonb),
                #{item.caseType}, #{item.country}, #{item.court}, #{item.courtLevel}, #{item.docType},
                CAST(#{disputeFocusJson} AS jsonb), #{item.judgmentDate}, #{item.procedure},
                CAST(#{applicableLawsJson} AS jsonb), #{item.caseLevel}, CAST(#{embedding} AS public.vector),
                #{modelName}, #{modelVersion}, CURRENT_TIMESTAMP
            )
            ON CONFLICT (external_case_id) WHERE external_case_id IS NOT NULL DO UPDATE SET
                title = EXCLUDED.title, case_cause = EXCLUDED.case_cause,
                case_cause_full_json = EXCLUDED.case_cause_full_json, case_type = EXCLUDED.case_type,
                country = EXCLUDED.country, court = EXCLUDED.court, court_level = EXCLUDED.court_level,
                doc_type = EXCLUDED.doc_type, dispute_focus_json = EXCLUDED.dispute_focus_json,
                judgment_date = EXCLUDED.judgment_date, procedure = EXCLUDED.procedure,
                applicable_law_json = EXCLUDED.applicable_law_json, case_level = EXCLUDED.case_level,
                embedding = EXCLUDED.embedding, embedding_model_name = EXCLUDED.embedding_model_name,
                embedding_model_version = EXCLUDED.embedding_model_version, embedded_at = CURRENT_TIMESTAMP
            RETURNING typical_case_id
            """)
    long upsertTypicalCase(@Param("item") RetrievalContract.NormalizedTypicalCase item,
                           @Param("caseCausesJson") String caseCausesJson,
                           @Param("disputeFocusJson") String disputeFocusJson,
                           @Param("applicableLawsJson") String applicableLawsJson,
                           @Param("embedding") String embedding,
                           @Param("modelName") String modelName,
                           @Param("modelVersion") String modelVersion);

    @Insert("""
            INSERT INTO lexpro.typical_case_content (typical_case_id, content, fact)
            VALUES (#{typicalCaseId}, #{content}, #{fact})
            ON CONFLICT (typical_case_id) DO UPDATE
            SET content = EXCLUDED.content, fact = EXCLUDED.fact
            """)
    int upsertTypicalCaseContent(@Param("typicalCaseId") long typicalCaseId,
                                 @Param("content") String content,
                                 @Param("fact") String fact);

    @Select({
            "<script>",
            "SELECT tc.typical_case_id, tc.external_case_id, tc.title, tc.case_cause,",
            "tc.case_cause_full_json::text AS case_cause_full_json, tc.case_type, tc.country,",
            "tc.court, tc.court_level, tc.doc_type, tc.dispute_focus_json::text AS dispute_focus_json,",
            "tc.judgment_date, tc.procedure, tc.applicable_law_json::text AS applicable_law_json,",
            "tc.case_level, tc.embedding_model_name, tc.embedding_model_version, tc.embedded_at,",
            "tc.created_at, tc.updated_at, NULL::text AS content, NULL::text AS fact,",
            "(f.user_id IS NOT NULL) AS favorite",
            "FROM lexpro.typical_case tc",
            "LEFT JOIN lexpro.typical_case_favorite f",
            "ON f.typical_case_id = tc.typical_case_id AND f.user_id = #{userId}",
            "WHERE 1=1",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (tc.title ILIKE '%' || #{keyword} || '%' OR tc.case_cause ILIKE '%' || #{keyword} || '%')",
            "</if>",
            "<if test='caseCause != null and caseCause != &quot;&quot;'>AND tc.case_cause = #{caseCause}</if>",
            "<if test='caseType != null and caseType != &quot;&quot;'>AND tc.case_type = #{caseType}</if>",
            "<if test='favoritesOnly'>AND f.user_id IS NOT NULL</if>",
            "ORDER BY tc.judgment_date DESC NULLS LAST, tc.typical_case_id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<TypicalCaseRecord> selectTypicalCases(@Param("userId") long userId,
                                                @Param("keyword") String keyword,
                                                @Param("caseCause") String caseCause,
                                                @Param("caseType") String caseType,
                                                @Param("favoritesOnly") boolean favoritesOnly,
                                                @Param("limit") int limit,
                                                @Param("offset") long offset);

    @Select({
            "<script>",
            "SELECT count(*) FROM lexpro.typical_case tc",
            "<if test='favoritesOnly'>",
            "JOIN lexpro.typical_case_favorite f ON f.typical_case_id = tc.typical_case_id AND f.user_id = #{userId}",
            "</if>",
            "WHERE 1=1",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (tc.title ILIKE '%' || #{keyword} || '%' OR tc.case_cause ILIKE '%' || #{keyword} || '%')",
            "</if>",
            "<if test='caseCause != null and caseCause != &quot;&quot;'>AND tc.case_cause = #{caseCause}</if>",
            "<if test='caseType != null and caseType != &quot;&quot;'>AND tc.case_type = #{caseType}</if>",
            "</script>"
    })
    long countTypicalCases(@Param("userId") long userId,
                           @Param("keyword") String keyword,
                           @Param("caseCause") String caseCause,
                           @Param("caseType") String caseType,
                           @Param("favoritesOnly") boolean favoritesOnly);

    @Select("""
            SELECT tc.typical_case_id, tc.external_case_id, tc.title, tc.case_cause,
                   tc.case_cause_full_json::text AS case_cause_full_json, tc.case_type, tc.country,
                   tc.court, tc.court_level, tc.doc_type, tc.dispute_focus_json::text AS dispute_focus_json,
                   tc.judgment_date, tc.procedure, tc.applicable_law_json::text AS applicable_law_json,
                   tc.case_level, tc.embedding_model_name, tc.embedding_model_version, tc.embedded_at,
                   tc.created_at, tc.updated_at, tcc.content, tcc.fact,
                   (f.user_id IS NOT NULL) AS favorite
            FROM lexpro.typical_case tc
            LEFT JOIN lexpro.typical_case_content tcc USING (typical_case_id)
            LEFT JOIN lexpro.typical_case_favorite f
              ON f.typical_case_id = tc.typical_case_id AND f.user_id = #{userId}
            WHERE tc.typical_case_id = #{typicalCaseId}
            """)
    TypicalCaseRecord selectTypicalCase(@Param("typicalCaseId") long typicalCaseId,
                                         @Param("userId") long userId);

    @Insert("""
            INSERT INTO lexpro.typical_case_favorite (user_id, typical_case_id)
            VALUES (#{userId}, #{typicalCaseId}) ON CONFLICT DO NOTHING
            """)
    int addFavorite(@Param("userId") long userId, @Param("typicalCaseId") long typicalCaseId);

    @Delete("""
            DELETE FROM lexpro.typical_case_favorite
            WHERE user_id = #{userId} AND typical_case_id = #{typicalCaseId}
            """)
    int removeFavorite(@Param("userId") long userId, @Param("typicalCaseId") long typicalCaseId);

    @Select("""
            SELECT summary_text FROM lexpro.case_summary
            WHERE case_id = #{caseId} AND summary_id = #{summaryId}
            """)
    String selectSummaryText(@Param("caseId") long caseId, @Param("summaryId") long summaryId);

    @Select({
            "<script>",
            "SELECT count(*) FROM lexpro.typical_case WHERE typical_case_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int countTypicalCaseIds(@Param("ids") List<Long> ids);

    @Insert("""
            INSERT INTO lexpro.case_recommendation (
                case_id, source_summary_id, query_fact_text, query_fact_embedding,
                query_dispute_focus_json, model_name, model_version, prompt_version,
                query_parameters_json, request_id, duration_ms, created_by
            ) VALUES (
                #{caseId}, #{sourceSummaryId}, #{queryFactText}, CAST(#{queryEmbedding} AS public.vector),
                CAST(#{queryDisputeFocusJson} AS jsonb), #{modelName}, #{modelVersion}, #{pipelineVersion},
                CAST(#{queryParametersJson} AS jsonb), #{requestId}, #{durationMs}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "recommendId", keyColumn = "recommend_id")
    int insertRecommendation(RecommendationWrite write);

    @Insert("""
            INSERT INTO lexpro.case_recommendation_item (
                recommend_id, case_id, typical_case_id, rank_no, similarity_score, reason_json
            ) VALUES (
                #{recommendId}, #{caseId}, #{typicalCaseId}, #{rank}, #{score}, CAST(#{reasonJson} AS jsonb)
            )
            """)
    int insertRecommendationItem(@Param("recommendId") long recommendId,
                                 @Param("caseId") long caseId,
                                 @Param("typicalCaseId") long typicalCaseId,
                                 @Param("rank") int rank,
                                 @Param("score") BigDecimal score,
                                 @Param("reasonJson") String reasonJson);

    @Select("""
            SELECT recommend_id, case_id, source_summary_id, query_fact_text,
                   query_dispute_focus_json::text AS query_dispute_focus_json,
                   model_name, model_version, prompt_version,
                   query_parameters_json::text AS query_parameters_json,
                   request_id, duration_ms, created_by, created_at
            FROM lexpro.case_recommendation
            WHERE case_id = #{caseId}
            ORDER BY recommend_id DESC
            """)
    List<RecommendationRecord> selectRecommendationHistory(@Param("caseId") long caseId);

    @Select("""
            SELECT recommend_id, case_id, source_summary_id, query_fact_text,
                   query_dispute_focus_json::text AS query_dispute_focus_json,
                   model_name, model_version, prompt_version,
                   query_parameters_json::text AS query_parameters_json,
                   request_id, duration_ms, created_by, created_at
            FROM lexpro.case_recommendation
            WHERE case_id = #{caseId} AND recommend_id = #{recommendId}
            """)
    RecommendationRecord selectRecommendation(@Param("caseId") long caseId,
                                               @Param("recommendId") long recommendId);

    @Select("""
            SELECT i.item_id, i.typical_case_id, i.rank_no, i.similarity_score,
                   i.reason_json::text AS reason_json, tc.external_case_id, tc.title,
                   tc.case_cause, tc.court, tc.court_level, tc.judgment_date,
                   (f.user_id IS NOT NULL) AS favorite
            FROM lexpro.case_recommendation_item i
            JOIN lexpro.typical_case tc USING (typical_case_id)
            LEFT JOIN lexpro.typical_case_favorite f
              ON f.typical_case_id = i.typical_case_id AND f.user_id = #{userId}
            WHERE i.case_id = #{caseId} AND i.recommend_id = #{recommendId}
            ORDER BY i.rank_no
            """)
    List<RecommendationItemRecord> selectRecommendationItems(@Param("caseId") long caseId,
                                                              @Param("recommendId") long recommendId,
                                                              @Param("userId") long userId);
}
