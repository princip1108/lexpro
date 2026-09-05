package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

public record PartnerRecommendationFiltersRequest(
        @Size(max = 255) String title,
        @Size(max = 50) List<@Size(max = 255) String> caseCauses,
        @Size(max = 100) List<@Size(max = 500) String> applicableLaws,
        @Pattern(regexp = "普通案例|典型案例|参考性案例|指导性案例") String caseLevel,
        @Pattern(regexp = "基层法院|中级法院|高级法院|最高法|最高检|人民法院案例库") String courtLevel,
        @Size(max = 100) String region,
        LocalDate judgmentDateThrough,
        @Size(max = 100) String procedure,
        @Size(max = 50) String docType,
        @Size(max = 255) String court,
        @Pattern(regexp = "刑事|民事|行政|公益诉讼|执行|赔偿") String caseType
) {}
