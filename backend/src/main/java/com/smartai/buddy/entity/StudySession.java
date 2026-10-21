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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions")
public class StudySession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private StudyPlan plan;

	@Column(nullable = false, length = 120)
	private String subject;

	@Column(name = "session_date", nullable = false)
	private LocalDate sessionDate;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "actual_duration_minutes")
	private Integer actualDurationMinutes;

	@Column(name = "actual_started_at")
	private LocalDateTime actualStartedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected StudySession() {
	}

	public StudySession(StudyPlan plan, String subject, LocalDate sessionDate, int durationMinutes) {
		this.plan = plan;
		this.subject = subject;
		this.sessionDate = sessionDate;
		this.durationMinutes = durationMinutes;
	}

	public Long getId() {
		return id;
	}

	public StudyPlan getPlan() {
		return plan;
	}

	public String getSubject() {
		return subject;
	}

	public LocalDate getSessionDate() {
		return sessionDate;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public Integer getActualDurationMinutes() {
		return actualDurationMinutes;
	}

	public LocalDateTime getActualStartedAt() {
		return actualStartedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void recordActualDuration(int minutes, LocalDateTime recordedAt) {
		actualDurationMinutes = minutes;
		actualStartedAt = minutes == 0 ? recordedAt : recordedAt.minusMinutes(minutes);
	}

	public void markCompleted(LocalDateTime completedTime) {
		if (completedAt == null) {
			completedAt = completedTime;
		}
	}
}
