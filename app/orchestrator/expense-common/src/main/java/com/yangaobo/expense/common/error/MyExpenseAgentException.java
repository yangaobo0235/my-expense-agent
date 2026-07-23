package com.yangaobo.expense.common.error;

import java.util.Objects;

public class MyExpenseAgentException extends RuntimeException {

    private final MyExpenseAgentErrorCode code;

    public MyExpenseAgentException(MyExpenseAgentErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MyExpenseAgentException(MyExpenseAgentErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MyExpenseAgentErrorCode code() {
        return code;
    }
}
