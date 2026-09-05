package com.lexpro.lexprobackend.recommendation.partner;

public class PartnerTypicalCaseClientException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        ANALYSIS_EXPIRED,
        RESPONSE_INVALID
    }

    private final Kind kind;

    public PartnerTypicalCaseClientException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
