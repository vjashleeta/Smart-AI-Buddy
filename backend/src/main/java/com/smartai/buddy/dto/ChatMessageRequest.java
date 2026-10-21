package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
	@NotBlank(message = "Enter a question.")
	@Size(max = 4000, message = "Questions must be no longer than 4000 characters.")
	String message,
	Long conversationId,
	@Size(max = 100, message = "Request IDs must be no longer than 100 characters.")
	String requestId
) {

	public ChatMessageRequest(String message) {
		this(message, null, null);
	}
}
