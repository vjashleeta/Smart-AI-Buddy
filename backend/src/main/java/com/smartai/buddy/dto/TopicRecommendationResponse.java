package com.smartai.buddy.dto;

import java.util.List;

public record TopicRecommendationResponse(
	String topic,
	String source,
	String explanation,
	List<LearningResourceResponse> resources
) {
	public TopicRecommendationResponse {
		resources = List.copyOf(resources);
	}
}
