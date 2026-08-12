package com.lexpro.lexprobackend.report.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReportExportProperties.class)
public class ReportConfig {
}
