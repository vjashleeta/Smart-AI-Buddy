package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStudentProfileRequest(
	@NotBlank(message = "Name is required.")
	@Size(max = 120, message = "Name must be at most 120 characters.")
	String name
) {
}
