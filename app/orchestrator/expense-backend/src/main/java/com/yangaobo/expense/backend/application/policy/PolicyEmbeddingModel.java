package com.yangaobo.expense.backend.application.policy;

public interface PolicyEmbeddingModel {

    float[] embed(String text);

    String modelName();
}
