package com.lexpro.lexprobackend.dossier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "lexpro.dossier")
public class DossierStorageProperties {

    private Path localRoot = Path.of("./storage");
    private DataSize maxFileSize = DataSize.ofMegabytes(25);
    private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "jpg", "jpeg", "png"
    ));
    private Set<String> allowedContentTypes = new LinkedHashSet<>(Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "image/jpeg",
            "image/png",
            "application/octet-stream"
    ));

    public Path getLocalRoot() { return localRoot; }

    public void setLocalRoot(Path localRoot) { this.localRoot = localRoot; }

    public DataSize getMaxFileSize() { return maxFileSize; }

    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }

    public Set<String> getAllowedExtensions() { return allowedExtensions; }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = normalize(allowedExtensions);
    }

    public Set<String> getAllowedContentTypes() { return allowedContentTypes; }

    public void setAllowedContentTypes(Set<String> allowedContentTypes) {
        this.allowedContentTypes = normalize(allowedContentTypes);
    }

    private Set<String> normalize(Set<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .forEach(normalized::add);
        }
        return normalized;
    }
}
