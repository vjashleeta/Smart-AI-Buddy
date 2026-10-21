package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_activities")
public class LearningActivity {

	private static final long MAX_HEARTBEAT_GAP_SECONDS = 30;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_type", nullable = false, length = 24)
	private LearningActivityType activityType;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "ended_at", nullable = false)
	private LocalDateTime endedAt;

	@Column(name = "last_heartbeat_at", nullable = false)
	private LocalDateTime lastHeartbeatAt;

	@Column(nullable = false)
	private boolean active = true;

	protected LearningActivity() {
	}

	public LearningActivity(User student, LearningActivityType activityType, LocalDateTime startedAt) {
		this.student = student;
		this.activityType = activityType;
		this.startedAt = startedAt;
		this.endedAt = startedAt;
		this.lastHeartbeatAt = startedAt;
	}

	public boolean heartbeat(LocalDateTime now) {
		if (!active) {
			return false;
		}
		long elapsedSeconds = Duration.between(lastHeartbeatAt, now).getSeconds();
		if (elapsedSeconds > MAX_HEARTBEAT_GAP_SECONDS) {
			active = false;
			return false;
		}
		if (elapsedSeconds > 0) {
			endedAt = now;
			lastHeartbeatAt = now;
		}
		return true;
	}

	public void stop(LocalDateTime now) {
		if (!active) {
			return;
		}
		long elapsedSeconds = Duration.between(lastHeartbeatAt, now).getSeconds();
		if (elapsedSeconds > 0 && elapsedSeconds <= MAX_HEARTBEAT_GAP_SECONDS) {
			endedAt = now;
		}
		active = false;
	}

	public Long getId() {
		return id;
	}

	public User getStudent() {
		return student;
	}

	public LearningActivityType getActivityType() {
		return activityType;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public boolean isActive() {
		return active;
	}
}
