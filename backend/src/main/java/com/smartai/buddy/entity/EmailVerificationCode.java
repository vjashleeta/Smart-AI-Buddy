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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "email_verification_codes",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_email_verification_user_purpose",
		columnNames = {"user_id", "purpose"}
	)
)
public class EmailVerificationCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
	private VerificationPurpose purpose;

	@Column(name = "code_hash", nullable = false, length = 60)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "resend_count", nullable = false)
	private int resendCount;

	@Column(name = "resend_window_started_at", nullable = false)
	private Instant resendWindowStartedAt;

	@Column(name = "last_sent_at", nullable = false)
	private Instant lastSentAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected EmailVerificationCode() {
	}

	public EmailVerificationCode(
		User user,
		VerificationPurpose purpose,
		String codeHash,
		Instant expiresAt,
		Instant sentAt
	) {
		this.user = user;
		this.purpose = purpose;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.resendWindowStartedAt = sentAt;
		this.lastSentAt = sentAt;
		this.resendCount = 1;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public VerificationPurpose getPurpose() {
		return purpose;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public int getResendCount() {
		return resendCount;
	}

	public Instant getResendWindowStartedAt() {
		return resendWindowStartedAt;
	}

	public Instant getLastSentAt() {
		return lastSentAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public void recordFailedAttempt() {
		attempts++;
	}

	public void consume(Instant now) {
		consumedAt = now;
	}

	public void replaceCode(String codeHash, Instant expiresAt, Instant sentAt, boolean resetWindow) {
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.lastSentAt = sentAt;
		this.attempts = 0;
		this.consumedAt = null;
		if (resetWindow) {
			resendWindowStartedAt = sentAt;
			resendCount = 1;
		} else {
			resendCount++;
		}
	}

	public void recordFailedResend(Instant attemptedAt, boolean resetWindow) {
		lastSentAt = attemptedAt;
		if (resetWindow) {
			resendWindowStartedAt = attemptedAt;
			resendCount = 1;
		} else {
			resendCount++;
		}
	}

	public boolean canResend(Instant now, java.time.Duration cooldown, java.time.Duration window, int maximumSends) {
		if (consumedAt != null) {
			return false;
		}
		if (resendWindowStartedAt.plus(window).compareTo(now) <= 0) {
			return true;
		}
		return resendCount < maximumSends && lastSentAt.plus(cooldown).compareTo(now) <= 0;
	}
}
