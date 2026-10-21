package com.smartai.buddy.controller;

import com.smartai.buddy.dto.ApiError;
import com.smartai.buddy.dto.ChatMessageResponse;
import com.smartai.buddy.dto.ChatTurn;
import com.smartai.buddy.dto.SocraticRequest;
import com.smartai.buddy.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/socratic")
public class SocraticController {

	private static final int MAX_MESSAGE_LENGTH = 4000;
	private final ChatService chatService;

	public SocraticController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public ChatMessageResponse guide(@Valid @RequestBody SocraticRequest request) {
		List<ChatTurn> history = request.history() == null
			? List.of()
			: request.history().stream()
				.filter(turn -> turn != null
					&& ("user".equals(turn.role()) || "model".equals(turn.role()))
					&& turn.content() != null
					&& !turn.content().isBlank()
					&& turn.content().length() <= MAX_MESSAGE_LENGTH)
				.toList();
		return chatService.answerSocratic(request.message(), history);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleTutorFailure(ResponseStatusException exception) {
		String message = exception.getReason() == null
			? "The Socratic tutor could not respond."
			: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message, Map.of()));
	}
}
