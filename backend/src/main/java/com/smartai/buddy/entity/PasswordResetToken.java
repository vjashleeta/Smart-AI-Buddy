package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(
	name = "password_reset_tokens",
	uniqueConstraints = @UniqueConstraint(name = "uk_password_reset_user", columnNames = "user_id")
)
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "request_window_started_at", nullable = false)
	private Instant requestWindowStartedAt;

	@Column(name = "last_requested_at", nullable = false)
	private Instant lastRequestedAt;

	@Column(name = "request_count", nullable = false)
	private int requestCount;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(User user, String tokenHash, Instant expiresAt, Instant requestedAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.requestWindowStartedAt = requestedAt;
		this.lastRequestedAt = requestedAt;
		this.requestCount = 1;
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public Instant getRequestWindowStartedAt() {
		return requestWindowStartedAt;
	}

	public Instant getLastRequestedAt() {
		return lastRequestedAt;
	}

	public int getRequestCount() {
		return requestCount;
	}

	public boolean canRequest(Instant now, Duration cooldown, Duration window, int maximumRequests) {
		if (!requestWindowStartedAt.plus(window).isAfter(now)) {
			return true;
		}
		return requestCount < maximumRequests && !lastRequestedAt.plus(cooldown).isAfter(now);
	}

	public void issue(String tokenHash, Instant expiresAt, Instant requestedAt, Duration window) {
		if (!requestWindowStartedAt.plus(window).isAfter(requestedAt)) {
			requestWindowStartedAt = requestedAt;
			requestCount = 1;
		} else {
			requestCount++;
		}
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.lastRequestedAt = requestedAt;
		this.consumedAt = null;
	}

	public void consume(Instant now) {
		consumedAt = now;
	}
}
