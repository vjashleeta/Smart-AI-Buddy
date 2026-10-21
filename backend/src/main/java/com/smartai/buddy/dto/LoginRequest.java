package com.smartai.buddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
	@NotBlank(message = "College email is required.")
	@Email(message = "Enter a valid college email address.")
	@Size(max = 254, message = "Email must be at most 254 characters.")
	String email,

	@NotBlank(message = "Password is required.")
	String password
) {
}
