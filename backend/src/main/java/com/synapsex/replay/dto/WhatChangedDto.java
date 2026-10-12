package com.synapsex.replay.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WhatChangedDto(
    String incidentId,
    Instant baselineWindowStart,
    Instant baselineWindowEnd,
    Instant incidentWindowStart,
    Instant incidentWindowEnd,
    Map<String, MetricDeltaDto> metricDeltas,
    List<String> newErrorPatterns,
    List<String> configurationChanges,
    Map<String, String> dependencyHealth
) {
    public record MetricDeltaDto(
        double baselineAvg,
        double incidentAvg,
        double deltaAbsolute,
        double deltaPercentage
    ) {}
}
