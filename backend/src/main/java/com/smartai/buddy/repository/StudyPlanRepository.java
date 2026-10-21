package com.smartai.buddy.repository;

import com.smartai.buddy.entity.StudyPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

	Optional<StudyPlan> findByStudent_Id(Long studentId);
}
