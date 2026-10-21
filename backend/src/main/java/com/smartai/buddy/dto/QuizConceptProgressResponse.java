package com.smartai.buddy.dto;

public record QuizConceptProgressResponse(
	Long conceptId,
	Long materialId,
	String topic,
	String status,
	int consecutiveCorrect,
	int masteryTarget,
	int totalAttempts
) {
}
