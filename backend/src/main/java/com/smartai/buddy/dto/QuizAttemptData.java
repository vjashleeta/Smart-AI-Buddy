package com.smartai.buddy.dto;

import java.time.LocalDateTime;

public record QuizAttemptData(
	Long attemptId,
	LocalDateTime completedAt,
	int correctAnswers,
	int totalQuestions,
	String topic
) {
}
