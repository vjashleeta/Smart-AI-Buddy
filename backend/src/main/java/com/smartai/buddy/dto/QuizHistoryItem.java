package com.smartai.buddy.dto;

import java.time.LocalDateTime;

public record QuizHistoryItem(
	Long attemptId,
	String topic,
	LocalDateTime completedAt,
	int correctAnswers,
	int totalQuestions,
	Double scorePercent
) {
}
