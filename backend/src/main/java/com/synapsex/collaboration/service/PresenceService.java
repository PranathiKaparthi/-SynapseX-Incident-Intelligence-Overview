package com.synapsex.collaboration.service;

import com.synapsex.collaboration.dto.PresenceHeartbeatDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private final StringRedisTemplate redisTemplate;

    // In-memory fallback tracking when Redis is not available or in local dev
    private final Map<UUID, Map<UUID, PresenceHeartbeatDto>> localPresence = new ConcurrentHashMap<>();

    public PresenceService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordHeartbeat(UUID incidentId, UUID userId, String userName, String status) {
        PresenceHeartbeatDto dto = new PresenceHeartbeatDto(
                incidentId, userId, userName, status, Instant.now().toEpochMilli());

        localPresence.computeIfAbsent(incidentId, k -> new ConcurrentHashMap<>()).put(userId, dto);

        if (redisTemplate != null) {
            try {
                String key = "presence:incident:" + incidentId + ":user:" + userId;
                redisTemplate.opsForValue().set(key, userName + ":" + status, java.time.Duration.ofSeconds(30));
            } catch (Exception ignored) {}
        }
    }

    public List<PresenceHeartbeatDto> getOnlineUsers(UUID incidentId) {
        Map<UUID, PresenceHeartbeatDto> roomMap = localPresence.get(incidentId);
        if (roomMap == null) return List.of();

        long cutoff = Instant.now().toEpochMilli() - 30_000; // 30 second TTL
        List<PresenceHeartbeatDto> active = new ArrayList<>();

        roomMap.forEach((userId, dto) -> {
            if (dto.timestamp() >= cutoff) {
                active.add(dto);
            }
        });

        return active;
    }
}
