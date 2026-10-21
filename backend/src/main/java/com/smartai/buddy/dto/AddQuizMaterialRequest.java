package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddQuizMaterialRequest(
	@NotBlank String topic,
	@NotBlank @Size(max = 12000) String sourceText
) {
}
