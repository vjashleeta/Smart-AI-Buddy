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
@Table(name = "quiz_study_materials")
public class StudyMaterial {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Column(nullable = false, length = 200)
	private String topic;

	@Column(name = "source_text", nullable = false, length = 12000)
	private String sourceText;

	@Column(name = "source_filename", length = 255)
	private String sourceFilename;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected StudyMaterial() {
	}

	public StudyMaterial(User student, String topic, String sourceText) {
		this(student, topic, sourceText, null);
	}

	public StudyMaterial(User student, String topic, String sourceText, String sourceFilename) {
		this.student = student;
		this.topic = topic;
		this.sourceText = sourceText;
		this.sourceFilename = sourceFilename;
	}

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public User getStudent() {
		return student;
	}

	public String getTopic() {
		return topic;
	}

	public String getSourceText() {
		return sourceText;
	}

	public String getSourceFilename() {
		return sourceFilename;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void markDeleted(LocalDateTime time) {
		if (deletedAt == null) {
			deletedAt = time;
		}
	}
}
