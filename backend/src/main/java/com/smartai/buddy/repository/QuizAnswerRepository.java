package com.smartai.buddy.repository;

import com.smartai.buddy.entity.QuizAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

	boolean existsByAttemptQuestion_Id(Long attemptQuestionId);

	long countByAttemptQuestion_Attempt_Id(Long attemptId);

	List<QuizAnswer> findAllByAttemptQuestion_Attempt_IdOrderByAnsweredAtAsc(Long attemptId);
}
