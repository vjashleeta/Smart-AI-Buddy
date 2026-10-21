package com.smartai.buddy.dto;

import java.time.LocalDateTime;

public record QuizMaterialResponse(
	Long id,
	String topic,
	String originalFilename,
	int conceptCount,
	LocalDateTime createdAt
) {
}
