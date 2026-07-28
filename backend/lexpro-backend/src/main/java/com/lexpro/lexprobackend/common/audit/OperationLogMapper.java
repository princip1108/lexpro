package com.lexpro.lexprobackend.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogMapper {

    @Insert("""
            INSERT INTO lexpro.operation_log (
                user_id,
                case_id,
                operation_type,
                object_type,
                object_id,
                operation_result,
                detail,
                request_id
            ) VALUES (
                #{event.userId},
                #{event.caseId},
                #{event.operationType},
                #{event.objectType},
                #{event.objectId},
                #{event.result},
                CAST(#{detailJson} AS jsonb),
                #{requestId}
            )
            """)
    int insert(
            @Param("event") AuditEvent event,
            @Param("detailJson") String detailJson,
            @Param("requestId") String requestId
    );
}
