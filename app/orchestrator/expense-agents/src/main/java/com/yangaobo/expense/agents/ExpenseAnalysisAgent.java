package com.yangaobo.expense.agents;

/**
 * Project-owned boundary for AI-assisted analysis. LangChain4j implementation
 * details must remain behind this package boundary.
 */
public interface ExpenseAnalysisAgent<I, O> {

    O analyze(I input);
}
