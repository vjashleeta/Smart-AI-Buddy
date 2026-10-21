package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record QuizAnswerRequest(
	@NotNull Long conceptId,
	@NotBlank @Pattern(regexp = "[A-D]") String answer
) {
}
