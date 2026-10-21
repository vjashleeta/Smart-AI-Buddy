package com.smartai.buddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
	@NotBlank(message = "Name is required.")
	@Size(max = 120, message = "Name must be at most 120 characters.")
	String name,

	@NotBlank(message = "College email is required.")
	@Email(message = "Enter a valid college email address.")
	@Size(max = 254, message = "Email must be at most 254 characters.")
	String email,

	@NotBlank(message = "Password is required.")
	@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
	String password
) {
}
