package com.smartai.buddy.repository;

import com.smartai.buddy.entity.StudyMaterial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

	List<StudyMaterial> findAllByStudent_IdOrderByCreatedAtDesc(Long studentId);

	List<StudyMaterial> findAllByStudent_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long studentId);

	java.util.Optional<StudyMaterial> findByIdAndStudent_IdAndDeletedAtIsNull(Long id, Long studentId);

	long countByStudent_Id(Long studentId);
}
