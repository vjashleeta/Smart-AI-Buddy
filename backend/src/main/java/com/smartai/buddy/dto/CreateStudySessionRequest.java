package com.smartai.buddy.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateStudySessionRequest(
	@NotBlank @Size(max = 120) String subject,
	@NotNull @FutureOrPresent LocalDate sessionDate,
	@Min(1) @Max(1440) int durationMinutes
) {
}
