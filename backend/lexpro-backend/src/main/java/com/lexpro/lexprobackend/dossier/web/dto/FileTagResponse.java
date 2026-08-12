package com.lexpro.lexprobackend.dossier.web.dto;

import com.lexpro.lexprobackend.dossier.domain.FileTag;

import java.time.OffsetDateTime;

public record FileTagResponse(Long tagId, Long caseId, String tagName, String color, OffsetDateTime createdAt) {
    public static FileTagResponse from(FileTag tag) {
        return new FileTagResponse(tag.getTagId(), tag.getCaseId(), tag.getTagName(), tag.getColor(),
                tag.getCreatedAt());
    }
}
