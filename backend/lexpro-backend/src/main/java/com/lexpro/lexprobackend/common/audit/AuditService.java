package com.lexpro.lexprobackend.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(AuditEvent event) {
        operationLogMapper.insert(event, serializeDetail(event), currentRequestId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEvent event) {
        operationLogMapper.insert(event, serializeDetail(event), currentRequestId());
    }

    private String serializeDetail(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event.detail());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit detail must be JSON serializable", exception);
        }
    }

    private String currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return RequestIdFilter.getOrCreateRequestId(request);
        }
        return null;
    }
}
