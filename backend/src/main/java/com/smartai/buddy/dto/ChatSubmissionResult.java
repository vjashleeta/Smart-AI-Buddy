package com.smartai.buddy.dto;

import org.springframework.http.HttpStatus;

public record ChatSubmissionResult(ChatMessageResponse response, HttpStatus status) {
}
