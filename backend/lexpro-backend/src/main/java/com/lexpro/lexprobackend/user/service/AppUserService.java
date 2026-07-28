package com.lexpro.lexprobackend.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
