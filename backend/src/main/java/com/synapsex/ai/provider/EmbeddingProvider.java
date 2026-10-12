package com.synapsex.ai.provider;

public interface EmbeddingProvider {
    float[] embed(String text);
    int getDimension();
}
