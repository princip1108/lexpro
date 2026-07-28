package com.lexpro.lexprobackend.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AppUserService {

    private final AppUserMapper appUserMapper;

    public AppUserService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(PageRequest pageRequest) {
        Page<AppUser> page = appUserMapper.selectPage(
                new Page<>(pageRequest.page(), pageRequest.size()),
                Wrappers.<AppUser>lambdaQuery().orderByAsc(AppUser::getUserId)
        );

        return PageResponse.from(page, UserSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(long userId) {
        UserAccount account = findAccountById(userId);
        return UserDetailResponse.from(account, appUserMapper.selectPermissionCodes(account.getRoleId()));
    }

    @Transactional(readOnly = true)
    public UserAccount findAccountByUsername(String username) {
        return appUserMapper.selectAccountByUsername(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "User not found",
                        "USER_NOT_FOUND",
                        "The requested user does not exist"
                ));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccountForAuthentication(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return appUserMapper.selectAccountByUsername(username.trim());
    }

    @Transactional(readOnly = true)
    public List<String> findPermissionCodes(long roleId) {
        return List.copyOf(appUserMapper.selectPermissionCodes(roleId));
    }

    @Transactional(readOnly = true)
    public UserAccount findAccountById(long userId) {
        return appUserMapper.selectAccountById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "User not found",
                        "USER_NOT_FOUND",
                        "The requested user does not exist"
                ));
    }
}
