package com.smartai.buddy.dto;

import java.util.List;

public record QuizQuestionResponse(
	Long conceptId,
	String topic,
	int position,
	int totalQuestions,
	String prompt,
	List<QuizOptionResponse> options
) {
	public QuizQuestionResponse {
		options = List.copyOf(options);
	}
}
