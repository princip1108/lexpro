package com.lexpro.lexprobackend.user.web;

import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.user.service.UserAdministrationService;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import com.lexpro.lexprobackend.user.web.dto.CreateUserRequest;
import com.lexpro.lexprobackend.user.web.dto.ResetUserPasswordRequest;
import com.lexpro.lexprobackend.user.web.dto.UpdateUserStatusRequest;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AppUserService appUserService;
    private final UserAdministrationService userAdministrationService;

    public UserController(
            AppUserService appUserService,
            UserAdministrationService userAdministrationService
    ) {
        this.appUserService = appUserService;
        this.userAdministrationService = userAdministrationService;
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

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserDetailResponse> createUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserDetailResponse response = userAdministrationService.createUser(currentUserId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.userId())).body(response);
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Enable or disable a user")
    public UserDetailResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return userAdministrationService.updateStatus(currentUserId(jwt), userId, request.status());
    }

    @PutMapping("/{userId}/password")
    @Operation(summary = "Reset a user's password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody ResetUserPasswordRequest request
    ) {
        userAdministrationService.resetPassword(currentUserId(jwt), userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
