package com.lexpro.lexprobackend.dossier.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DossierStorageProperties.class)
public class DossierStorageConfig {
}
