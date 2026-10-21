package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
	name = "quiz_concepts",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_quiz_concept_material_sequence",
		columnNames = {"material_id", "sequence_number"}
	)
)
public class QuizConcept {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "material_id", nullable = false)
	private StudyMaterial material;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@Column(name = "source_sentence", nullable = false, length = 2000)
	private String sourceSentence;

	@Column(name = "question_prompt", nullable = false, length = 2000)
	private String questionPrompt;

	@Column(name = "expected_answer", nullable = false, length = 120)
	private String expectedAnswer;

	@Column(name = "consecutive_correct", nullable = false)
	private int consecutiveCorrect;

	@Column(name = "total_attempts", nullable = false)
	private int totalAttempts;

	@Column(name = "last_correct")
	private Boolean lastCorrect;

	@Column(name = "last_attempt_at")
	private LocalDateTime lastAttemptAt;

	protected QuizConcept() {
	}

	public QuizConcept(
		StudyMaterial material,
		int sequenceNumber,
		String sourceSentence,
		String questionPrompt,
		String expectedAnswer
	) {
		this.material = material;
		this.sequenceNumber = sequenceNumber;
		this.sourceSentence = sourceSentence;
		this.questionPrompt = questionPrompt;
		this.expectedAnswer = expectedAnswer;
	}

	public void recordAnswer(boolean correct, LocalDateTime answeredAt) {
		totalAttempts++;
		consecutiveCorrect = correct ? consecutiveCorrect + 1 : 0;
		lastCorrect = correct;
		lastAttemptAt = answeredAt;
	}

	public Long getId() {
		return id;
	}

	public StudyMaterial getMaterial() {
		return material;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public String getSourceSentence() {
		return sourceSentence;
	}

	public String getQuestionPrompt() {
		return questionPrompt;
	}

	public String getExpectedAnswer() {
		return expectedAnswer;
	}

	public int getConsecutiveCorrect() {
		return consecutiveCorrect;
	}

	public int getTotalAttempts() {
		return totalAttempts;
	}

	public Boolean getLastCorrect() {
		return lastCorrect;
	}

	public LocalDateTime getLastAttemptAt() {
		return lastAttemptAt;
	}
}
