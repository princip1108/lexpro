package com.lexpro.lexprobackend.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lexpro.bootstrap.admin", name = "enabled", havingValue = "true")
public class DevelopmentAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentAdminBootstrap.class);

    private final DevelopmentAdminBootstrapService bootstrapService;
    private final String username;
    private final String password;
    private final String realName;
    private final String organizationCode;
    private final String organizationName;

    public DevelopmentAdminBootstrap(
            DevelopmentAdminBootstrapService bootstrapService,
            @Value("${lexpro.bootstrap.admin.username}") String username,
            @Value("${lexpro.bootstrap.admin.password}") String password,
            @Value("${lexpro.bootstrap.admin.real-name}") String realName,
            @Value("${lexpro.bootstrap.admin.organization-code}") String organizationCode,
            @Value("${lexpro.bootstrap.admin.organization-name}") String organizationName
    ) {
        this.bootstrapService = bootstrapService;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.organizationCode = organizationCode;
        this.organizationName = organizationName;
    }

    @Override
    public void run(ApplicationArguments args) {
        DevelopmentAdminBootstrapService.BootstrapResult result = bootstrapService.bootstrap(
                new DevelopmentAdminBootstrapService.BootstrapRequest(
                        username,
                        password,
                        realName,
                        organizationCode,
                        organizationName
                )
        );
        if (result.created()) {
            log.info("development_admin_bootstrapped userId={}", result.userId());
        } else {
            log.info("development_admin_bootstrap_skipped reason=users_already_exist");
        }
    }
}
