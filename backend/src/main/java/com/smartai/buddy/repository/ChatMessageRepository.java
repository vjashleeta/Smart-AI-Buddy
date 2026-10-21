package com.smartai.buddy.repository;

import com.smartai.buddy.entity.ChatMessage;
import com.smartai.buddy.entity.ChatMessageRole;
import com.smartai.buddy.entity.ChatMessageStatus;
import com.smartai.buddy.entity.ChatMessageRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	List<ChatMessage> findAllByConversation_Student_IdAndRoleOrderByCreatedAtAsc(
		Long studentId,
		ChatMessageRole role
	);

	List<ChatMessage> findAllByConversation_IdOrderByCreatedAtAscIdAsc(Long conversationId);

	List<ChatMessage> findTop10ByConversation_IdAndRoleAndStatusOrderByCreatedAtDescIdDesc(
		Long conversationId,
		ChatMessageRole role,
		ChatMessageStatus status
	);

	List<ChatMessage> findAllByConversation_IdAndReplyToRequestIdIn(
		Long conversationId,
		Collection<String> requestIds
	);

	Optional<ChatMessage> findByConversation_IdAndRequestId(Long conversationId, String requestId);

	Optional<ChatMessage> findByConversation_IdAndReplyToRequestId(Long conversationId, String requestId);
}
