package com.smartai.buddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
	@NotBlank(message = "College email is required.")
	@Email(message = "Enter a valid college email address.")
	@Size(max = 254, message = "Email must be at most 254 characters.")
	String email,

	@NotBlank(message = "Verification code is required.")
	@Pattern(regexp = "\\d{8}", message = "Enter the 8-digit verification code.")
	String code
) {
}
