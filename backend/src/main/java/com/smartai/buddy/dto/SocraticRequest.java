package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SocraticRequest(
	@NotBlank(message = "Enter a topic, question, or response.")
	@Size(max = 4000, message = "Messages must be no longer than 4000 characters.")
	String message,
	@Size(max = 20, message = "A tutor session can include up to 20 prior messages.")
	List<ChatTurn> history
) {
}
