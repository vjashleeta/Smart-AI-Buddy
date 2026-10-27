package com.smartai.buddy.controller;

import com.smartai.buddy.dto.EmailVerificationRequest;
import com.smartai.buddy.dto.ResendVerificationRequest;
import com.smartai.buddy.dto.RegistrationRequest;
import com.smartai.buddy.dto.RegistrationResponse;
import com.smartai.buddy.service.EmailVerificationService;
import com.smartai.buddy.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

	private final RegistrationService registrationService;
	private final EmailVerificationService emailVerificationService;

	public RegistrationController(
		RegistrationService registrationService,
		EmailVerificationService emailVerificationService
	) {
		this.registrationService = registrationService;
		this.emailVerificationService = emailVerificationService;
	}

	@PostMapping("/api/auth/register")
	public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
	}

	@PostMapping("/api/auth/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
		emailVerificationService.verify(request);
	}

	@PostMapping("/api/auth/resend-verification")
	public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		if (!emailVerificationService.resend(request.email())) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		return ResponseEntity.noContent().build();
	}
}
