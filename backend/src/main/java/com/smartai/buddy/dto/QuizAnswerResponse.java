package com.smartai.buddy.dto;

public record QuizAnswerResponse(
	Long attemptId,
	Long conceptId,
	boolean correct,
	String correctOption,
	String correctAnswer,
	String explanation,
	int correctAnswers,
	int totalQuestions,
	boolean completed,
	String conceptStatus,
	int consecutiveCorrect,
	int masteryTarget
) {
}
