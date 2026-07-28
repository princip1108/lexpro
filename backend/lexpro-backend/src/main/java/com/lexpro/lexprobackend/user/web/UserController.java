package com.lexpro.lexprobackend.user.web;

import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AppUserService appUserService;

    public UserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    @Operation(summary = "List users")
    public PageResponse<UserSummaryResponse> listUsers(
            @Valid @ParameterObject @ModelAttribute PageRequest pageRequest
    ) {
        return appUserService.listUsers(pageRequest);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user details")
    public UserDetailResponse getUser(@PathVariable long userId) {
        return appUserService.getUser(userId);
    }
}
