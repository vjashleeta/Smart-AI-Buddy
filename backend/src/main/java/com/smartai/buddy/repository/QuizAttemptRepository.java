package com.smartai.buddy.repository;

import com.smartai.buddy.entity.QuizAttempt;
import com.smartai.buddy.dto.QuizAttemptData;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

	Optional<QuizAttempt> findByIdAndStudent_Id(Long id, Long studentId);

	long countByStudent_Id(Long studentId);

	long countByStudent_IdAndCompletedAtIsNotNull(Long studentId);

	List<QuizAttempt> findAllByStudent_IdAndCompletedAtIsNotNullOrderByCompletedAtAsc(Long studentId);

	@Query("""
		select avg((attempt.correctAnswers * 100.0) / attempt.totalQuestions)
		from QuizAttempt attempt
		where attempt.student.id = :studentId
			and attempt.completedAt is not null
			and attempt.totalQuestions > 0
			and attempt.correctAnswers >= 0
			and attempt.correctAnswers <= attempt.totalQuestions
		""")
	Double findAverageScorePercentByStudentId(@Param("studentId") Long studentId);

	@Query("""
		select max((attempt.correctAnswers * 100.0) / attempt.totalQuestions)
		from QuizAttempt attempt
		where attempt.student.id = :studentId
			and attempt.completedAt is not null
			and attempt.totalQuestions > 0
			and attempt.correctAnswers >= 0
			and attempt.correctAnswers <= attempt.totalQuestions
		""")
	Double findBestScorePercentByStudentId(@Param("studentId") Long studentId);

	@Query("""
		select new com.smartai.buddy.dto.QuizAttemptData(
			attempt.id,
			attempt.completedAt,
			attempt.correctAnswers,
			attempt.totalQuestions,
			coalesce(material.topic, 'Uncategorized')
		)
		from QuizAttempt attempt
		left join QuizAttemptQuestion attemptQuestion on attemptQuestion.attempt = attempt
		left join attemptQuestion.concept concept
		left join concept.material material
		where attempt.student.id = :studentId
			and attempt.completedAt is not null
		group by attempt.id, attempt.completedAt, attempt.correctAnswers, attempt.totalQuestions, material.topic
		order by attempt.completedAt desc, attempt.id desc
		""")
	List<QuizAttemptData> findCompletedAttemptDataByStudentId(
		@Param("studentId") Long studentId,
		Pageable pageable
	);
}
