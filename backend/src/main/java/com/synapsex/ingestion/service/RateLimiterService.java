package com.synapsex.ingestion.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long rateLimitPerSecond;
    private final long burstCapacity;

    public RateLimiterService(
            @Value("${synapsex.ingestion.rate-limit-per-second:1000}") long rateLimitPerSecond,
            @Value("${synapsex.ingestion.burst-capacity:2000}") long burstCapacity) {
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.burstCapacity = burstCapacity;
    }

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, this::createNewBucket);
        return bucket.tryConsume(1);
    }

    public boolean tryConsume(String key, long tokens) {
        if (tokens <= 0) return true;
        Bucket bucket = buckets.computeIfAbsent(key, this::createNewBucket);
        return bucket.tryConsume(tokens);
    }

    public long getAvailableTokens(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, this::createNewBucket);
        return bucket.getAvailableTokens();
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(rateLimitPerSecond, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
