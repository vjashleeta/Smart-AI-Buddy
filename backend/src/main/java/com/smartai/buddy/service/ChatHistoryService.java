package com.smartai.buddy.service;

import com.smartai.buddy.dto.ChatConversationResponse;
import com.smartai.buddy.dto.ChatMessageResponse;
import com.smartai.buddy.dto.ChatStoredMessageResponse;
import com.smartai.buddy.dto.ChatSubmissionResult;
import com.smartai.buddy.dto.ChatTurn;
import com.smartai.buddy.entity.ChatConversation;
import com.smartai.buddy.entity.ChatMessage;
import com.smartai.buddy.entity.ChatMessageRole;
import com.smartai.buddy.entity.ChatMessageStatus;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.ChatConversationRepository;
import com.smartai.buddy.repository.ChatMessageRepository;
import com.smartai.buddy.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatHistoryService {

	private final UserRepository userRepository;
	private final ChatConversationRepository conversationRepository;
	private final ChatMessageRepository messageRepository;
	private final ChatService chatService;

	public ChatHistoryService(
		UserRepository userRepository,
		ChatConversationRepository conversationRepository,
		ChatMessageRepository messageRepository,
		ChatService chatService
	) {
		this.userRepository = userRepository;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.chatService = chatService;
	}

	@Transactional
	public ChatConversationResponse createConversation(String studentEmail) {
		User student = findStudent(studentEmail);
		return toConversationResponse(
			conversationRepository.saveAndFlush(new ChatConversation(student))
		);
	}

	@Transactional(readOnly = true)
	public List<ChatConversationResponse> listConversations(String studentEmail) {
		User student = findStudent(studentEmail);
		return conversationRepository.findAllByStudent_IdOrderByUpdatedAtDescIdDesc(student.getId())
			.stream()
			.map(ChatHistoryService::toConversationResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ChatStoredMessageResponse> getMessages(String studentEmail, Long conversationId) {
		User student = findStudent(studentEmail);
		ChatConversation conversation = findOwnedConversation(conversationId, student.getId());
		return messageRepository.findAllByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId())
			.stream()
			.map(ChatHistoryService::toMessageResponse)
			.toList();
	}

	@Transactional
	public ChatSubmissionResult submit(
		String studentEmail,
		Long conversationId,
		String requestId,
		String question
	) {
		User student = findStudent(studentEmail);
		ChatConversation conversation = conversationId == null
			? conversationRepository.saveAndFlush(new ChatConversation(student))
			: conversationRepository.findOwnedForUpdate(conversationId, student.getId())
				.orElseThrow(ChatHistoryService::conversationNotFound);
		String effectiveRequestId = requestId == null || requestId.isBlank()
			? UUID.randomUUID().toString()
			: requestId.strip();

		ChatMessage userMessage = messageRepository
			.findByConversation_IdAndRequestId(conversation.getId(), effectiveRequestId)
			.orElse(null);
		if (userMessage != null && !userMessage.getContent().equals(question.strip())) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"This request ID was already used for a different question."
			);
		}
		if (userMessage != null && userMessage.getStatus() == ChatMessageStatus.COMPLETED) {
			ChatMessage savedAnswer = messageRepository
				.findByConversation_IdAndReplyToRequestId(conversation.getId(), effectiveRequestId)
				.orElseThrow(() -> new ResponseStatusException(
					HttpStatus.CONFLICT,
					"The saved answer is incomplete. Retry this request."
				));
			return new ChatSubmissionResult(
				new ChatMessageResponse(
					savedAnswer.getContent(),
					conversation.getId(),
					effectiveRequestId,
					null
				),
				HttpStatus.OK
			);
		}
		if (userMessage == null) {
			userMessage = messageRepository.saveAndFlush(ChatMessage.userQuestion(
				conversation,
				question.strip(),
				effectiveRequestId
			));
			conversation.setTitleIfNew(question.strip());
		} else {
			userMessage.retry();
		}

		try {
			ChatMessageResponse generated = chatService.answer(question, recentCompletedTurns(conversation.getId()));
			ChatMessage assistantMessage = messageRepository.save(ChatMessage.assistantAnswer(
				conversation,
				generated.answer(),
				effectiveRequestId
			));
			userMessage.complete();
			conversation.touch();
			return new ChatSubmissionResult(
				new ChatMessageResponse(
					assistantMessage.getContent(),
					conversation.getId(),
					effectiveRequestId,
					null
				),
				HttpStatus.OK
			);
		} catch (ResponseStatusException exception) {
			userMessage.fail();
			conversation.touch();
			return new ChatSubmissionResult(
				new ChatMessageResponse(
					null,
					conversation.getId(),
					effectiveRequestId,
					exception.getReason()
				),
				HttpStatus.valueOf(exception.getStatusCode().value())
			);
		}
	}

	private List<ChatTurn> recentCompletedTurns(Long conversationId) {
		List<ChatMessage> recentQuestions = messageRepository
			.findTop10ByConversation_IdAndRoleAndStatusOrderByCreatedAtDescIdDesc(
				conversationId,
				ChatMessageRole.USER,
				ChatMessageStatus.COMPLETED
			);
		if (recentQuestions.isEmpty()) {
			return List.of();
		}

		Map<String, ChatMessage> answers = new HashMap<>();
		messageRepository.findAllByConversation_IdAndReplyToRequestIdIn(
			conversationId,
			recentQuestions.stream().map(ChatMessage::getRequestId).toList()
		).forEach(answer -> answers.put(answer.getReplyToRequestId(), answer));

		List<ChatMessage> orderedQuestions = new ArrayList<>(recentQuestions);
		orderedQuestions.sort(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId));
		List<ChatTurn> turns = new ArrayList<>();
		for (ChatMessage question : orderedQuestions) {
			ChatMessage answer = answers.get(question.getRequestId());
			if (answer != null) {
				turns.add(new ChatTurn("user", question.getContent()));
				turns.add(new ChatTurn("model", answer.getContent()));
			}
		}
		return turns;
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}

	private ChatConversation findOwnedConversation(Long conversationId, Long studentId) {
		return conversationRepository.findByIdAndStudent_Id(conversationId, studentId)
			.orElseThrow(ChatHistoryService::conversationNotFound);
	}

	private static ResponseStatusException conversationNotFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
	}

	private static ChatConversationResponse toConversationResponse(ChatConversation conversation) {
		return new ChatConversationResponse(
			conversation.getId(),
			conversation.getTitle(),
			conversation.getCreatedAt(),
			conversation.getUpdatedAt()
		);
	}

	private static ChatStoredMessageResponse toMessageResponse(ChatMessage message) {
		return new ChatStoredMessageResponse(
			message.getId(),
			message.getRole(),
			message.getContent(),
			message.getRequestId(),
			message.getStatus(),
			message.getCreatedAt()
		);
	}
}
