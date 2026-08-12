package com.lexpro.lexprobackend.report.service;

public record ExportedReport(byte[] content, String contentType, String fileName) {
}
