package com.smartai.buddy.dto;

import java.util.List;

public record PlannerResponse(Long planId, List<StudySessionResponse> sessions) {

	public PlannerResponse {
		sessions = List.copyOf(sessions);
	}
}
