package com.smartai.buddy.service;

import com.smartai.buddy.dto.EmailVerificationRequest;
import com.smartai.buddy.entity.EmailVerificationCode;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.entity.VerificationPurpose;
import com.smartai.buddy.repository.EmailVerificationCodeRepository;
import com.smartai.buddy.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {

	private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
	private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
	private static final Duration RESEND_WINDOW = Duration.ofHours(1);
	private static final int MAX_ATTEMPTS = 5;
	private static final int MAX_SENDS_PER_WINDOW = 3;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final EmailVerificationCodeRepository codeRepository;
	private final PasswordEncoder passwordEncoder;
	private final VerificationEmailSender emailSender;
	private final Clock clock;

	public EmailVerificationService(
		UserRepository userRepository,
		EmailVerificationCodeRepository codeRepository,
		PasswordEncoder passwordEncoder,
		VerificationEmailSender emailSender,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.codeRepository = codeRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailSender = emailSender;
		this.clock = clock;
	}

	@Transactional
	public boolean createAndSendCode(User user) {
		Instant now = clock.instant();
		String code = generateCode();
		EmailVerificationCode verificationCode = new EmailVerificationCode(
			user,
			VerificationPurpose.EMAIL_VERIFICATION,
			passwordEncoder.encode(code),
			now.plus(CODE_LIFETIME),
			now
		);
		codeRepository.save(verificationCode);
		return sendCode(user.getCollegeEmail(), code);
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public void verify(EmailVerificationRequest request) {
		String normalizedEmail = normalizeEmail(request.email());
		User user = userRepository.findByCollegeEmail(normalizedEmail)
			.orElseThrow(EmailVerificationService::invalidCode);
		if (user.isEmailVerified()) {
			throw invalidCode();
		}

		EmailVerificationCode verificationCode = codeRepository.findForUpdate(
			user.getId(),
			VerificationPurpose.EMAIL_VERIFICATION
		).orElseThrow(EmailVerificationService::invalidCode);
		Instant now = clock.instant();
		if (verificationCode.getConsumedAt() != null || !verificationCode.getExpiresAt().isAfter(now)) {
			throw invalidCode();
		}
		if (verificationCode.getAttempts() >= MAX_ATTEMPTS) {
			throw new ResponseStatusException(
				HttpStatus.TOO_MANY_REQUESTS,
				"Too many incorrect verification attempts. Request a new code."
			);
		}
		if (!passwordEncoder.matches(request.code(), verificationCode.getCodeHash())) {
			verificationCode.recordFailedAttempt();
			codeRepository.save(verificationCode);
			if (verificationCode.getAttempts() >= MAX_ATTEMPTS) {
				throw new ResponseStatusException(
					HttpStatus.TOO_MANY_REQUESTS,
					"Too many incorrect verification attempts. Request a new code."
				);
			}
			throw invalidCode();
		}

		verificationCode.consume(now);
		user.verifyEmail();
		userRepository.save(user);
		codeRepository.save(verificationCode);
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public boolean resend(String email) {
		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByCollegeEmail(normalizedEmail).orElse(null);
		if (user == null || user.isEmailVerified()) {
			return true;
		}

		EmailVerificationCode verificationCode = codeRepository.findForUpdate(
			user.getId(),
			VerificationPurpose.EMAIL_VERIFICATION
		).orElse(null);
		if (verificationCode == null) {
			return createAndSendCode(user);
		}

		Instant now = clock.instant();
		if (!verificationCode.canResend(now, RESEND_COOLDOWN, RESEND_WINDOW, MAX_SENDS_PER_WINDOW)) {
			throw new ResponseStatusException(
				HttpStatus.TOO_MANY_REQUESTS,
				"Please wait before requesting another verification code."
			);
		}

		boolean resetWindow = !verificationCode.getResendWindowStartedAt().plus(RESEND_WINDOW).isAfter(now);
		String code = generateCode();
		if (!sendCode(normalizedEmail, code)) {
			verificationCode.recordFailedResend(now, resetWindow);
			codeRepository.save(verificationCode);
			return false;
		}

		verificationCode.replaceCode(passwordEncoder.encode(code), now.plus(CODE_LIFETIME), now, resetWindow);
		codeRepository.save(verificationCode);
		return true;
	}

	private boolean sendCode(String email, String code) {
		try {
			emailSender.sendVerificationCode(email, code);
			return true;
		} catch (MailException exception) {
			return false;
		}
	}

	private static String generateCode() {
		return "%08d".formatted(SECURE_RANDOM.nextInt(100_000_000));
	}

	private static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static ResponseStatusException invalidCode() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification code is invalid or expired.");
	}
}
