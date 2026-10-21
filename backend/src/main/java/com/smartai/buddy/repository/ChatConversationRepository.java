package com.smartai.buddy.repository;

import com.smartai.buddy.entity.ChatConversation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

	List<ChatConversation> findAllByStudent_IdOrderByUpdatedAtDescIdDesc(Long studentId);

	Optional<ChatConversation> findByIdAndStudent_Id(Long id, Long studentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select conversation from ChatConversation conversation "
		+ "where conversation.id = :conversationId and conversation.student.id = :studentId")
	Optional<ChatConversation> findOwnedForUpdate(
		@Param("conversationId") Long conversationId,
		@Param("studentId") Long studentId
	);
}
