package com.lexpro.lexprobackend.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppUserService {

    private final AppUserMapper appUserMapper;

    public AppUserService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers() {
        return appUserMapper.selectList(
                        Wrappers.<AppUser>lambdaQuery()
                                .orderByAsc(AppUser::getUserId)
                ).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }
}
