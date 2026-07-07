package com.yangaobo.expense.common.error;

import java.util.Objects;

public class ExpenseFlowException extends RuntimeException {

    private final ExpenseFlowErrorCode code;

    public ExpenseFlowException(ExpenseFlowErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExpenseFlowException(ExpenseFlowErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExpenseFlowErrorCode code() {
        return code;
    }
}
