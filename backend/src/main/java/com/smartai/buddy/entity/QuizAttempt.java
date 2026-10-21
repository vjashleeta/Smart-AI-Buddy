package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Column(name = "total_questions", nullable = false)
	private int totalQuestions;

	@Column(name = "correct_answers", nullable = false)
	private int correctAnswers;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected QuizAttempt() {
	}

	public QuizAttempt(User student, int totalQuestions) {
		this.student = student;
		this.totalQuestions = totalQuestions;
	}

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}

	public void recordCorrectAnswer() {
		correctAnswers++;
	}

	public void complete(LocalDateTime time) {
		completedAt = time;
	}

	public Long getId() {
		return id;
	}

	public User getStudent() {
		return student;
	}

	public int getTotalQuestions() {
		return totalQuestions;
	}

	public int getCorrectAnswers() {
		return correctAnswers;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public boolean isCompleted() {
		return completedAt != null;
	}
}
