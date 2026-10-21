package com.smartai.buddy.dto;

import java.util.List;

public record QuizOverviewResponse(
	List<QuizMaterialResponse> materials,
	List<QuizConceptProgressResponse> concepts,
	int notYetTested,
	int inProgress,
	int needsRevision,
	int mastered,
	int masteryTarget,
	int maxTopicCharacters,
	int maxPdfBytes,
	int maxPdfPages,
	int maxExtractedCharacters
) {
	public QuizOverviewResponse {
		materials = List.copyOf(materials);
		concepts = List.copyOf(concepts);
	}
}
