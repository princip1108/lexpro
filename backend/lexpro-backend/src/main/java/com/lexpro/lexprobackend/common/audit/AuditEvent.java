package com.lexpro.lexprobackend.common.audit;

import java.util.Map;

public record AuditEvent(
        Long userId,
        Long caseId,
        String operationType,
        String objectType,
        String objectId,
        AuditResult result,
        Map<String, Object> detail
) {

    public AuditEvent {
        if (operationType == null || operationType.isBlank()) {
            throw new IllegalArgumentException("operationType must not be blank");
        }
        if (operationType.length() > 50) {
            throw new IllegalArgumentException("operationType must not exceed 50 characters");
        }
        if (objectType != null && objectType.length() > 50) {
            throw new IllegalArgumentException("objectType must not exceed 50 characters");
        }
        if (objectId != null && objectId.length() > 100) {
            throw new IllegalArgumentException("objectId must not exceed 100 characters");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }
}
