package com.smartai.buddy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecordActualStudyTimeRequest(
	@NotNull @Min(1) @Max(1440) Integer actualDurationMinutes
) {
}
