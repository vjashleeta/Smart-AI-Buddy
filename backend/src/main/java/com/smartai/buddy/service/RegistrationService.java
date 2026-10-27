package com.smartai.buddy.service;

import com.smartai.buddy.dto.RegistrationRequest;
import com.smartai.buddy.dto.RegistrationResponse;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.exception.DuplicateEmailException;
import com.smartai.buddy.exception.InvalidRegistrationException;
import com.smartai.buddy.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationService emailVerificationService;

	public RegistrationService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		EmailVerificationService emailVerificationService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailVerificationService = emailVerificationService;
	}

	@Transactional
	public RegistrationResponse register(RegistrationRequest request) {
		String email = request.email().strip().toLowerCase(Locale.ROOT);
		String name = request.name().strip();
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new InvalidRegistrationException("Password must be no longer than 72 UTF-8 bytes.");
		}
		if (userRepository.existsByCollegeEmail(email)) {
			throw new DuplicateEmailException();
		}

		User user = new User(name, email, passwordEncoder.encode(request.password()), false);
		try {
			user = userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
		boolean verificationEmailSent = emailVerificationService.createAndSendCode(user);
		return new RegistrationResponse(
			user.getId(),
			user.getName(),
			user.getCollegeEmail(),
			verificationEmailSent
		);
	}
}
