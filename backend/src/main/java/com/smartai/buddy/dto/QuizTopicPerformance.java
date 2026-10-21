package com.smartai.buddy.dto;

public record QuizTopicPerformance(
	String topic,
	int completedAttempts,
	Double averageScorePercent,
	Double bestScorePercent
) {
}
