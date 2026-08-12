package com.lexpro.lexprobackend.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexpro.report")
public class ReportExportProperties {

    private String pdfFontPath;

    public String getPdfFontPath() {
        return pdfFontPath;
    }

    public void setPdfFontPath(String pdfFontPath) {
        this.pdfFontPath = pdfFontPath;
    }
}
