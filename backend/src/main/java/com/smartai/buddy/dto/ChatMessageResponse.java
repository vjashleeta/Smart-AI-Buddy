package com.smartai.buddy.dto;

public record ChatMessageResponse(
	String answer,
	Long conversationId,
	String requestId,
	String message
) {

	public ChatMessageResponse(String answer) {
		this(answer, null, null, null);
	}
}
