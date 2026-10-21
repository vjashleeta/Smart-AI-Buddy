package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
	name = "chat_messages",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_chat_message_request",
		columnNames = {"conversation_id", "request_id"}
	),
	indexes = @Index(name = "idx_chat_message_conversation_created", columnList = "conversation_id, created_at")
)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private ChatConversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ChatMessageRole role;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "request_id", length = 100)
	private String requestId;

	@Column(name = "reply_to_request_id", length = 100)
	private String replyToRequestId;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private ChatMessageStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected ChatMessage() {
	}

	private ChatMessage(
		ChatConversation conversation,
		ChatMessageRole role,
		String content,
		String requestId,
		String replyToRequestId,
		ChatMessageStatus status
	) {
		this.conversation = conversation;
		this.role = role;
		this.content = content;
		this.requestId = requestId;
		this.replyToRequestId = replyToRequestId;
		this.status = status;
	}

	public static ChatMessage userQuestion(ChatConversation conversation, String content, String requestId) {
		return new ChatMessage(
			conversation,
			ChatMessageRole.USER,
			content,
			requestId,
			null,
			ChatMessageStatus.PENDING
		);
	}

	public static ChatMessage assistantAnswer(
		ChatConversation conversation,
		String content,
		String replyToRequestId
	) {
		return new ChatMessage(
			conversation,
			ChatMessageRole.ASSISTANT,
			content,
			null,
			replyToRequestId,
			null
		);
	}

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}

	public void complete() {
		status = ChatMessageStatus.COMPLETED;
	}

	public void fail() {
		status = ChatMessageStatus.FAILED;
	}

	public void retry() {
		status = ChatMessageStatus.PENDING;
	}

	public Long getId() {
		return id;
	}

	public ChatConversation getConversation() {
		return conversation;
	}

	public ChatMessageRole getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public String getRequestId() {
		return requestId;
	}

	public String getReplyToRequestId() {
		return replyToRequestId;
	}

	public ChatMessageStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
