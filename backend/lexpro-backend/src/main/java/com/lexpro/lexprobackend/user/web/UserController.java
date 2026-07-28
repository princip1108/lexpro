package com.lexpro.lexprobackend.user.web;

import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AppUserService appUserService;

    public UserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return appUserService.listUsers();
    }
}
