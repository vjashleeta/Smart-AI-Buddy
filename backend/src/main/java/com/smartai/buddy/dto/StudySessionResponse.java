package com.smartai.buddy.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudySessionResponse(
	Long id,
	String subject,
	LocalDate sessionDate,
	int durationMinutes,
	Integer actualDurationMinutes,
	LocalDateTime completedAt
) {
}
