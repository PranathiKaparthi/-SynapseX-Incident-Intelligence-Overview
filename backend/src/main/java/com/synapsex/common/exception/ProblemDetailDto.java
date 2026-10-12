package com.synapsex.common.exception;

import java.time.Instant;
import java.util.List;

public record ProblemDetailDto(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    String traceId,
    Instant timestamp,
    List<FieldErrorDto> fieldErrors
) {
    public record FieldErrorDto(String field, String message) {}
}
