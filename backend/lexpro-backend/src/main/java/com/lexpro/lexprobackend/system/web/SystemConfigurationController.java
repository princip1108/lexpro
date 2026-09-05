package com.lexpro.lexprobackend.system.web;

import com.lexpro.lexprobackend.common.audit.OperationLogMapper;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
@SecurityRequirement(name = "bearerAuth")
public class SystemConfigurationController {

    private final AiServiceProperties aiService;
    private final AiProcessingProperties externalAi;
    private final OperationLogMapper operationLogs;
    private final com.lexpro.lexprobackend.system.service.ModelConfigurationService modelConfigurations;

    public SystemConfigurationController(AiServiceProperties aiService, AiProcessingProperties externalAi,
                                         OperationLogMapper operationLogs,
                                         com.lexpro.lexprobackend.system.service.ModelConfigurationService modelConfigurations) {
        this.aiService = aiService;
        this.externalAi = externalAi;
        this.operationLogs = operationLogs;
        this.modelConfigurations = modelConfigurations;
    }

    @GetMapping("/model-configuration")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get deployment-managed model configuration without secrets")
    public ModelConfigurationResponse modelConfiguration() {
        if(modelConfigurations.enabled()){
            var models=modelConfigurations.list();
            return new ModelConfigurationResponse(models.stream().filter(row->row.active()).map(row->row.displayName()).findFirst().orElse("未配置生效模型"),
                    "auto",models.stream().map(row->new ModelEndpointResponse(row.id(),row.displayName(),row.modelName(),row.baseUrl(),row.apiKeyMasked(),row.enableThinking(),row.remark(),row.enabled(),row.active())).toList(),
                    new MinerUResponse(aiService.isEnabled(),aiService.getBaseUrl().toString()));
        }
        List<ModelEndpointResponse> items = new ArrayList<>();
        items.add(new ModelEndpointResponse("lexpro-internal", "LexPro", "LexPro_8B",
                aiService.getBaseUrl().toString(), "••••••••", false,
                "LexPro 多模态实体识别服务", aiService.isEnabled(), aiService.isEnabled()));
        if (externalAi.isEnabled()) {
            items.add(new ModelEndpointResponse("external-ai", externalAi.getModel(), externalAi.getModel(),
                    externalAi.getBaseUrl().toString(), "••••••••", externalAi.isEnableThinking(),
                    "法律要素、摘要、案卡和报告生成；部署配置只读", true, true));
        }
        String activeMode = externalAi.isEnabled() ? externalAi.getModel() : aiService.isEnabled() ? "LexPro" : "内置规则引擎(离线)";
        return new ModelConfigurationResponse(activeMode, "auto", items,
                new MinerUResponse(aiService.isEnabled(), aiService.getBaseUrl().toString()));
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List recent operation logs")
    public OperationLogListResponse operationLogs(@RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return new OperationLogListResponse(operationLogs.selectRecent(safeLimit).stream()
                .map(row -> new OperationLogResponse(row.logId(), row.operationType(),
                        readableDetail(row.detail()), row.username(), target(row), row.operationTime()))
                .toList());
    }

    private String target(OperationLogMapper.OperationLogRow row) {
        if (row.objectType() == null) return row.operationResult();
        return row.objectId() == null ? row.objectType() : row.objectType() + " #" + row.objectId();
    }

    private String readableDetail(String detail) {
        if (detail == null || "null".equals(detail) || "{}".equals(detail)) return "";
        return detail.length() > 500 ? detail.substring(0, 500) + "…" : detail;
    }

    public record ModelConfigurationResponse(String activeMode, String parseEngine,
                                             List<ModelEndpointResponse> items, MinerUResponse mineru) {}
    public record ModelEndpointResponse(String id, String displayName, String modelName, String baseUrl,
                                        String apiKeyMasked, boolean enableThinking, String remark,
                                        boolean enabled, boolean active) {}
    public record MinerUResponse(boolean enabled, String baseUrl) {}
    public record OperationLogListResponse(List<OperationLogResponse> items) {}
    public record OperationLogResponse(long operationId, String action, String detail, String username,
                                       String target, OffsetDateTime createdAt) {}
}
