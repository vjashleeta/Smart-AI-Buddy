package com.smartai.buddy.dto;

import java.util.Map;

public record ApiError(String message, Map<String, String> errors) {
}
