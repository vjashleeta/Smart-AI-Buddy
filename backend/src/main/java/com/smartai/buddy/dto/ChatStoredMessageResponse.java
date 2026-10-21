package com.smartai.buddy.dto;

import com.smartai.buddy.entity.ChatMessageRole;
import com.smartai.buddy.entity.ChatMessageStatus;
import java.time.LocalDateTime;

public record ChatStoredMessageResponse(
	Long id,
	ChatMessageRole role,
	String content,
	String requestId,
	ChatMessageStatus status,
	LocalDateTime createdAt
) {
}
