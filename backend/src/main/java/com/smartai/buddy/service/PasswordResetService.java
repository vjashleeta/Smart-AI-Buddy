package com.smartai.buddy.service;

import com.smartai.buddy.dto.ResetPasswordRequest;
import com.smartai.buddy.entity.PasswordResetToken;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.PasswordResetTokenRepository;
import com.smartai.buddy.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

	public static final String GENERIC_RESPONSE =
		"If an eligible account exists for that email, password reset instructions will be sent.";
	private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
	private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
	private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
	private static final int MAX_REQUESTS_PER_ACCOUNT = 3;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final VerificationEmailSender emailSender;
	private final PasswordResetRateLimiter rateLimiter;
	private final Clock clock;
	private final String publicBaseUrl;

	public PasswordResetService(
		UserRepository userRepository,
		PasswordResetTokenRepository tokenRepository,
		PasswordEncoder passwordEncoder,
		VerificationEmailSender emailSender,
		PasswordResetRateLimiter rateLimiter,
		Clock clock,
		@Value("${smart-ai-buddy.public-base-url:http://localhost:8080}") String publicBaseUrl
	) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailSender = emailSender;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	@Transactional
	public void requestReset(String email, String remoteAddress) {
		Instant now = clock.instant();
		if (!rateLimiter.allow(remoteAddress, now)) {
			return;
		}
		String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		User user = userRepository.findByCollegeEmail(normalizedEmail).orElse(null);
		if (user == null || !user.isEmailVerified()) {
			return;
		}

		PasswordResetToken existing = tokenRepository.findByUserIdForUpdate(user.getId()).orElse(null);
		if (existing != null
			&& !existing.canRequest(now, REQUEST_COOLDOWN, REQUEST_WINDOW, MAX_REQUESTS_PER_ACCOUNT)) {
			return;
		}

		String rawToken = generateToken();
		String resetLink = publicBaseUrl + "/pages/reset-password.html#token=" + rawToken;
		try {
			emailSender.sendPasswordResetLink(normalizedEmail, resetLink);
		} catch (MailException exception) {
			return;
		}

		String tokenHash = sha256(rawToken);
		if (existing == null) {
			tokenRepository.save(new PasswordResetToken(user, tokenHash, now.plus(TOKEN_LIFETIME), now));
		} else {
			existing.issue(tokenHash, now.plus(TOKEN_LIFETIME), now, REQUEST_WINDOW);
			tokenRepository.save(existing);
		}
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request, String remoteAddress) {
		Instant now = clock.instant();
		if (!rateLimiter.allow(remoteAddress, now)) {
			throw invalidToken();
		}
		if (!request.newPassword().equals(request.confirmPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
		}
		if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"New password must be no longer than 72 UTF-8 bytes."
			);
		}

		PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(sha256(request.token()))
			.orElseThrow(PasswordResetService::invalidToken);
		if (resetToken.getConsumedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
			throw invalidToken();
		}

		User user = resetToken.getUser();
		user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
		resetToken.consume(now);
		userRepository.save(user);
		tokenRepository.save(resetToken);
	}

	private static String generateToken() {
		byte[] randomBytes = new byte[32];
		SECURE_RANDOM.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private static ResponseStatusException invalidToken() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reset link is invalid or expired.");
	}
}
