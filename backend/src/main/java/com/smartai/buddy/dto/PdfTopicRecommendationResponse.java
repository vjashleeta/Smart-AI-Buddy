package com.smartai.buddy.dto;

import java.util.List;

public record PdfTopicRecommendationResponse(
	String summary,
	List<String> identifiedTopics,
	List<TopicRecommendationResponse> recommendations,
	int maxPdfBytes,
	int maxPdfPages,
	int maxExtractedCharacters
) {
	public PdfTopicRecommendationResponse {
		identifiedTopics = List.copyOf(identifiedTopics);
		recommendations = List.copyOf(recommendations);
	}
}
