package com.lexpro.lexprobackend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.auth.domain.AuthRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRole> {

    @Select("""
            SELECT p.permission_code
            FROM lexpro.auth_role_permission rp
            JOIN lexpro.auth_permission p ON p.permission_id = rp.permission_id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodes(@Param("roleId") long roleId);
}
