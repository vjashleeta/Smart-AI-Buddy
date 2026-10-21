package com.smartai.buddy.controller;

import com.smartai.buddy.dto.ApiError;
import com.smartai.buddy.dto.ChatConversationResponse;
import com.smartai.buddy.dto.ChatMessageRequest;
import com.smartai.buddy.dto.ChatMessageResponse;
import com.smartai.buddy.dto.ChatStoredMessageResponse;
import com.smartai.buddy.dto.ChatSubmissionResult;
import com.smartai.buddy.service.ChatHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/chat")
public class ChatController {

	private final ChatHistoryService chatHistoryService;

	public ChatController(ChatHistoryService chatHistoryService) {
		this.chatHistoryService = chatHistoryService;
	}

	@PostMapping
	public ResponseEntity<ChatMessageResponse> ask(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody ChatMessageRequest request
	) {
		ChatSubmissionResult result = chatHistoryService.submit(
			student.getUsername(),
			request.conversationId(),
			request.requestId(),
			request.message()
		);
		return ResponseEntity.status(result.status()).body(result.response());
	}

	@PostMapping("/conversations")
	@ResponseStatus(HttpStatus.CREATED)
	public ChatConversationResponse createConversation(@AuthenticationPrincipal UserDetails student) {
		return chatHistoryService.createConversation(student.getUsername());
	}

	@GetMapping("/conversations")
	public List<ChatConversationResponse> listConversations(@AuthenticationPrincipal UserDetails student) {
		return chatHistoryService.listConversations(student.getUsername());
	}

	@GetMapping("/conversations/{conversationId}/messages")
	public List<ChatStoredMessageResponse> getMessages(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long conversationId
	) {
		return chatHistoryService.getMessages(student.getUsername(), conversationId);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleChatFailure(ResponseStatusException exception) {
		String message = exception.getReason() == null
			? "AI Buddy could not answer your question."
			: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message, Map.of()));
	}
}
