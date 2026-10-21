package com.smartai.buddy.repository;

import com.smartai.buddy.entity.LearningActivity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningActivityRepository extends JpaRepository<LearningActivity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select activity from LearningActivity activity where activity.id = :id and activity.student.id = :studentId")
	Optional<LearningActivity> findOwnedForUpdate(
		@Param("id") Long id,
		@Param("studentId") Long studentId
	);

	List<LearningActivity> findAllByStudent_IdAndStartedAtBeforeAndEndedAtAfterOrderByStartedAtAsc(
		Long studentId,
		LocalDateTime before,
		LocalDateTime after
	);

	List<LearningActivity> findAllByStudent_IdOrderByStartedAtAsc(Long studentId);
}
