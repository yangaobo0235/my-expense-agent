package com.yangaobo.expense.common.error;

import java.util.Objects;

public class CampusFundFlowException extends RuntimeException {

    private final CampusFundFlowErrorCode code;

    public CampusFundFlowException(CampusFundFlowErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CampusFundFlowException(CampusFundFlowErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CampusFundFlowErrorCode code() {
        return code;
    }
}
