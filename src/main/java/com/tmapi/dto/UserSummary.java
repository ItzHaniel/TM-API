package com.tmapi.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * Lightweight user summary used as embedded DTO in Task/Workspace responses.
 * Avoids embedding the full User response recursively.
 */
public record UserSummary(
    UUID   id,
    String username,
    String email,
    String fullName
) implements Serializable {}
