package com.smartai.buddy.repository;

import com.smartai.buddy.entity.StudySession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

	List<StudySession> findAllByPlan_IdOrderBySessionDateAscIdAsc(Long planId);

	java.util.Optional<StudySession> findByIdAndPlan_Student_Id(Long id, Long studentId);

	List<StudySession>
		findAllByPlan_Student_IdAndCompletedAtIsNotNullAndActualDurationMinutesGreaterThanAndActualStartedAtLessThanAndActualStartedAtGreaterThanOrderByActualStartedAtAsc(
			Long studentId,
			int minimumActualDurationMinutes,
			java.time.LocalDateTime before,
			java.time.LocalDateTime after
		);

	List<StudySession> findAllByPlan_Student_IdAndCompletedAtIsNotNullAndActualDurationMinutesGreaterThanOrderByCompletedAtAsc(
		Long studentId,
		int minimumActualDurationMinutes
	);

	List<StudySession> findTop5ByPlan_IdAndSessionDateGreaterThanEqualOrderBySessionDateAscIdAsc(
		Long planId,
		java.time.LocalDate sessionDate
	);

	int countByPlan_IdAndSessionDateGreaterThanEqual(Long planId, java.time.LocalDate sessionDate);
}
