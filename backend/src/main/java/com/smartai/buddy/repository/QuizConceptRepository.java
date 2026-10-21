package com.smartai.buddy.repository;

import com.smartai.buddy.entity.QuizConcept;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizConceptRepository extends JpaRepository<QuizConcept, Long> {

	List<QuizConcept> findAllByMaterial_Student_IdOrderByMaterial_CreatedAtAscSequenceNumberAsc(Long studentId);

	List<QuizConcept> findAllByMaterial_IdAndMaterial_DeletedAtIsNullOrderBySequenceNumberAsc(Long materialId);

	Optional<QuizConcept> findByIdAndMaterial_Student_Id(Long id, Long studentId);

	long countByMaterial_Id(Long materialId);

	@Query("""
		select concept from QuizConcept concept
		where concept.material.student.id = :studentId and concept.material.deletedAt is null
		order by concept.material.createdAt asc, concept.sequenceNumber asc
		""")
	List<QuizConcept> findAllActiveByStudentId(@Param("studentId") Long studentId);
}
