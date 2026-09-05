package com.lexpro.lexprobackend.recommendation.partner;

public class AnalysisTokenException extends RuntimeException {

    public enum Kind {
        INVALID,
        EXPIRED,
        BINDING_MISMATCH
    }

    private final Kind kind;

    public AnalysisTokenException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
