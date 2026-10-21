package com.smartai.buddy.dto;

import java.time.LocalDateTime;

public record ChatConversationResponse(
	Long id,
	String title,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
}
