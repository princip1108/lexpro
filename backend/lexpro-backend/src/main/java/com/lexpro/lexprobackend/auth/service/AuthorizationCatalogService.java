package com.lexpro.lexprobackend.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lexpro.lexprobackend.auth.domain.AuthPermission;
import com.lexpro.lexprobackend.auth.domain.AuthRole;
import com.lexpro.lexprobackend.auth.mapper.AuthPermissionMapper;
import com.lexpro.lexprobackend.auth.mapper.AuthRoleMapper;
import com.lexpro.lexprobackend.auth.web.dto.PermissionResponse;
import com.lexpro.lexprobackend.auth.web.dto.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorizationCatalogService {

    private final AuthRoleMapper authRoleMapper;
    private final AuthPermissionMapper authPermissionMapper;

    public AuthorizationCatalogService(
            AuthRoleMapper authRoleMapper,
            AuthPermissionMapper authPermissionMapper
    ) {
        this.authRoleMapper = authRoleMapper;
        this.authPermissionMapper = authPermissionMapper;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return authRoleMapper.selectList(
                        Wrappers.<AuthRole>lambdaQuery().orderByAsc(AuthRole::getRoleCode)
                ).stream()
                .map(role -> RoleResponse.from(role, authRoleMapper.selectPermissionCodes(role.getRoleId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return authPermissionMapper.selectList(
                        Wrappers.<AuthPermission>lambdaQuery()
                                .orderByAsc(AuthPermission::getPermissionCode)
                ).stream()
                .map(PermissionResponse::from)
                .toList();
    }
}
