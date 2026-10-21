package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attempt_question_id", nullable = false, unique = true)
	private QuizAttemptQuestion attemptQuestion;

	@Column(name = "submitted_answer", nullable = false, length = 500)
	private String submittedAnswer;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "answered_at", nullable = false)
	private LocalDateTime answeredAt;

	protected QuizAnswer() {
	}

	public QuizAnswer(QuizAttemptQuestion attemptQuestion, String submittedAnswer, boolean correct) {
		this.attemptQuestion = attemptQuestion;
		this.submittedAnswer = submittedAnswer;
		this.correct = correct;
	}

	@PrePersist
	void prePersist() {
		answeredAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public QuizAttemptQuestion getAttemptQuestion() {
		return attemptQuestion;
	}

	public String getSubmittedAnswer() {
		return submittedAnswer;
	}

	public boolean isCorrect() {
		return correct;
	}

	public LocalDateTime getAnsweredAt() {
		return answeredAt;
	}
}
