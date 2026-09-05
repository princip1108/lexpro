package com.lexpro.lexprobackend.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

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

    @Select("""
            SELECT l.log_id, l.operation_type, l.object_type, l.object_id,
                   l.operation_result, l.detail::text AS detail,
                   coalesce(u.real_name, u.username, '系统') AS username,
                   l.operation_time
            FROM lexpro.operation_log l
            LEFT JOIN lexpro.app_user u ON u.user_id = l.user_id
            ORDER BY l.operation_time DESC, l.log_id DESC
            LIMIT #{limit}
            """)
    List<OperationLogRow> selectRecent(@Param("limit") int limit);

    record OperationLogRow(long logId, String operationType, String objectType, String objectId,
                           String operationResult, String detail, String username,
                           OffsetDateTime operationTime) {}
}
