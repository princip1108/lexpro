package com.lexpro.lexprobackend.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("""
            SELECT
                u.user_id,
                u.username,
                u.password_hash,
                u.real_name,
                u.status,
                u.created_at,
                u.updated_at,
                u.organization_id,
                o.organization_code,
                o.organization_name,
                u.role_id,
                r.role_code,
                r.role_name
            FROM lexpro.app_user u
            JOIN lexpro.auth_role r ON r.role_id = u.role_id
            LEFT JOIN lexpro.organization_unit o ON o.organization_id = u.organization_id
            WHERE u.user_id = #{userId}
            """)
    Optional<UserAccount> selectAccountById(@Param("userId") long userId);

    @Select("""
            SELECT
                u.user_id,
                u.username,
                u.password_hash,
                u.real_name,
                u.status,
                u.created_at,
                u.updated_at,
                u.organization_id,
                o.organization_code,
                o.organization_name,
                u.role_id,
                r.role_code,
                r.role_name
            FROM lexpro.app_user u
            JOIN lexpro.auth_role r ON r.role_id = u.role_id
            LEFT JOIN lexpro.organization_unit o ON o.organization_id = u.organization_id
            WHERE lower(u.username) = lower(#{username})
            """)
    Optional<UserAccount> selectAccountByUsername(@Param("username") String username);

    @Select("""
            SELECT p.permission_code
            FROM lexpro.auth_role_permission rp
            JOIN lexpro.auth_permission p ON p.permission_id = rp.permission_id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodes(@Param("roleId") long roleId);
}
