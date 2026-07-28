package com.lexpro.lexprobackend.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppUserServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapPagedUsersToSafeResponseDtos() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        AppUser user = new AppUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setPasswordHash("must-not-leak");
        Page<AppUser> resultPage = new Page<>(1, 20, 1);
        resultPage.setRecords(List.of(user));
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(resultPage);
        AppUserService service = new AppUserService(mapper);

        PageResponse<UserSummaryResponse> response = service.listUsers(new PageRequest(null, null));

        assertEquals(1, response.totalItems());
        assertEquals("admin", response.items().getFirst().username());
    }
}
