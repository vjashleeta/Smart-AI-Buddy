package com.smartai.buddy.repository;

import com.smartai.buddy.entity.QuizAttemptQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptQuestionRepository extends JpaRepository<QuizAttemptQuestion, Long> {

	List<QuizAttemptQuestion> findAllByAttempt_IdOrderByQuestionPositionAsc(Long attemptId);

	Optional<QuizAttemptQuestion> findByAttempt_IdAndConcept_Id(Long attemptId, Long conceptId);
}
