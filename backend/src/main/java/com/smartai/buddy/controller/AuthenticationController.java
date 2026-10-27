package com.smartai.buddy.controller;

import com.smartai.buddy.dto.ApiError;
import com.smartai.buddy.dto.CsrfTokenResponse;
import com.smartai.buddy.dto.ForgotPasswordRequest;
import com.smartai.buddy.dto.LoginRequest;
import com.smartai.buddy.dto.ResetPasswordRequest;
import com.smartai.buddy.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController {

	private static final String INVALID_CREDENTIALS = "Invalid email or password.";

	private final AuthenticationManager authenticationManager;
	private final PasswordResetService passwordResetService;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy =
		SecurityContextHolder.getContextHolderStrategy();

	public AuthenticationController(
		AuthenticationManager authenticationManager,
		PasswordResetService passwordResetService,
		SecurityContextRepository securityContextRepository,
		SessionAuthenticationStrategy sessionAuthenticationStrategy,
		CsrfTokenRepository csrfTokenRepository
	) {
		this.authenticationManager = authenticationManager;
		this.passwordResetService = passwordResetService;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	@GetMapping("/api/auth/csrf")
	public CsrfTokenResponse csrfToken(@RequestAttribute("_csrf") CsrfToken csrfToken) {
		return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
	}

	@PostMapping("/api/auth/forgot-password")
	public ResponseEntity<Map<String, String>> forgotPassword(
		@Valid @RequestBody ForgotPasswordRequest request,
		HttpServletRequest servletRequest
	) {
		passwordResetService.requestReset(request.email(), servletRequest.getRemoteAddr());
		return ResponseEntity.accepted().body(Map.of("message", PasswordResetService.GENERIC_RESPONSE));
	}

	@PostMapping("/api/auth/reset-password")
	public ResponseEntity<Void> resetPassword(
		@Valid @RequestBody ResetPasswordRequest request,
		HttpServletRequest servletRequest
	) {
		passwordResetService.resetPassword(request, servletRequest.getRemoteAddr());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<?> login(
		@Valid @RequestBody LoginRequest request,
		HttpServletRequest servletRequest,
		HttpServletResponse servletResponse
	) {
		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(
					request.email().strip().toLowerCase(Locale.ROOT),
					request.password()
				)
			);
		} catch (AuthenticationException exception) {
			return invalidCredentials();
		}

		servletRequest.getSession(true);
		sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
		csrfTokenRepository.saveToken(null, servletRequest, servletResponse);

		SecurityContext context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, servletRequest, servletResponse);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/auth/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		if (request.getSession(false) != null) {
			request.getSession(false).invalidate();
		}
		securityContextHolderStrategy.clearContext();
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<ApiError> invalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new ApiError(INVALID_CREDENTIALS, Map.of()));
	}
}
