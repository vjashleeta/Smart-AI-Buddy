package com.smartai.buddy.dto;

import java.util.List;

public record QuizAttemptResponse(
	Long attemptId,
	int totalQuestions,
	int correctAnswers,
	boolean completed,
	List<QuizQuestionResponse> questions
) {
	public QuizAttemptResponse {
		questions = List.copyOf(questions);
	}
}
