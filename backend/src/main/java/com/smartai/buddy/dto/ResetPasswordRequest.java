package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
	@NotBlank(message = "Reset token is required.")
	@Size(max = 128, message = "Reset token is invalid.")
	String token,

	@NotBlank(message = "New password is required.")
	@Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters.")
	String newPassword,

	@NotBlank(message = "Password confirmation is required.")
	String confirmPassword
) {
}
