package com.smartai.buddy;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.PasswordResetToken;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.PasswordResetTokenRepository;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.PasswordResetService;
import com.smartai.buddy.service.VerificationEmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetIntegrationTests {

	private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository resetTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private VerificationEmailSender emailSender;

	@MockitoBean
	private Clock clock;

	private String email;
	private String remoteAddress;

	@BeforeEach
	void setUp() {
		email = "reset-test-" + UUID.randomUUID() + "@college.edu";
		remoteAddress = "reset-test-" + UUID.randomUUID();
		org.mockito.Mockito.when(clock.instant()).thenReturn(START);
	}

	@AfterEach
	void cleanUp() {
		userRepository.findByCollegeEmail(email).ifPresent(user -> resetTokenRepository.deleteByUser_Id(user.getId()));
		userRepository.deleteByCollegeEmail(email);
	}

	@Test
	void returnsTheSameGenericResponseForKnownUnknownAndUnverifiedEmails() throws Exception {
		createUser(true);
		String knownResponse = requestReset(email, remoteAddress);
		String unknownResponse = requestReset("missing-" + email, remoteAddress);

		User unverified = userRepository.saveAndFlush(new User(
			"Unverified Student",
			"unverified-" + email,
			passwordEncoder.encode("oldPassword123"),
			false
		));
		String unverifiedResponse = requestReset(unverified.getCollegeEmail(), remoteAddress);

		org.junit.jupiter.api.Assertions.assertEquals(PasswordResetService.GENERIC_RESPONSE, knownResponse);
		org.junit.jupiter.api.Assertions.assertEquals(knownResponse, unknownResponse);
		org.junit.jupiter.api.Assertions.assertEquals(knownResponse, unverifiedResponse);
		verify(emailSender, times(1)).sendPasswordResetLink(eq(email), anyString());
	}

	@Test
	void storesOnlySha256TokenHashAndDoesNotBuildLinkFromHostHeader() throws Exception {
		createUser(true);
		String link = requestResetAndCaptureLink(email, remoteAddress);
		String token = tokenFromLink(link);
		PasswordResetToken storedToken = resetTokenRepository.findAll().stream()
			.filter(reset -> reset.getUser().getCollegeEmail().equals(email))
			.findFirst()
			.orElseThrow();

		org.junit.jupiter.api.Assertions.assertTrue(link.startsWith("http://localhost:8080/pages/reset-password.html#token="));
		org.junit.jupiter.api.Assertions.assertNotEquals(token, storedToken.getTokenHash());
		org.junit.jupiter.api.Assertions.assertEquals(sha256(token), storedToken.getTokenHash());
		org.junit.jupiter.api.Assertions.assertEquals(START.plusSeconds(15 * 60L), storedToken.getExpiresAt());
	}

	@Test
	void resetsPasswordAndAllowsLoginWithTheNewPassword() throws Exception {
		createUser(true);
		String token = tokenFromLink(requestResetAndCaptureLink(email, remoteAddress));

		mockMvc.perform(resetRequest(token, "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isNoContent());

		User user = userRepository.findByCollegeEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("newPassword456", user.getPasswordHash()));
		PasswordResetToken usedToken = resetTokenRepository.findAll().stream()
			.filter(reset -> reset.getUser().getCollegeEmail().equals(email))
			.findFirst()
			.orElseThrow();
		org.junit.jupiter.api.Assertions.assertNotNull(usedToken.getConsumedAt());

		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "newPassword456")))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "oldPassword123")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsInvalidExpiredConsumedAndSupersededTokens() throws Exception {
		createUser(true);
		String firstToken = tokenFromLink(requestResetAndCaptureLink(email, remoteAddress));

		mockMvc.perform(resetRequest("invalid-token", "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isBadRequest());

		org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(16 * 60L));
		mockMvc.perform(resetRequest(firstToken, "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The reset link is invalid or expired."));

		org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(61));
		String replacementToken = tokenFromLink(requestResetAndCaptureLink(email, remoteAddress));
		mockMvc.perform(resetRequest(firstToken, "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isBadRequest());
		mockMvc.perform(resetRequest(replacementToken, "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isNoContent());
		mockMvc.perform(resetRequest(replacementToken, "anotherPassword789", "anotherPassword789", remoteAddress))
			.andExpect(status().isBadRequest());
	}

	@Test
	void validatesPasswordConfirmationAndUtf8ByteLimit() throws Exception {
		createUser(true);
		String token = tokenFromLink(requestResetAndCaptureLink(email, remoteAddress));

		mockMvc.perform(resetRequest(token, "newPassword456", "differentPassword789", remoteAddress))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Passwords do not match."));

		String tooManyUtf8Bytes = "é".repeat(37);
		mockMvc.perform(resetRequest(token, tooManyUtf8Bytes, tooManyUtf8Bytes, remoteAddress))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("New password must be no longer than 72 UTF-8 bytes."));

		mockMvc.perform(resetRequest(token, "short", "short", remoteAddress))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.newPassword").exists());
	}

	@Test
	void limitsPerAccountRequestsWithoutChangingTheGenericResponse() throws Exception {
		createUser(true);
		String first = requestReset(email, remoteAddress);
		org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(61));
		String second = requestReset(email, remoteAddress);
		org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(122));
		String third = requestReset(email, remoteAddress);
		org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(183));
		String fourth = requestReset(email, remoteAddress);

		org.junit.jupiter.api.Assertions.assertEquals(first, second);
		org.junit.jupiter.api.Assertions.assertEquals(second, third);
		org.junit.jupiter.api.Assertions.assertEquals(third, fourth);
		verify(emailSender, times(3)).sendPasswordResetLink(eq(email), anyString());
	}

	@Test
	void rateLimitsForgotRequestsByRemoteAddress() throws Exception {
		String response = null;
		for (int request = 0; request < 11; request++) {
			String current = requestReset("unknown-" + request + "@college.edu", remoteAddress);
			if (response == null) {
				response = current;
			}
			org.junit.jupiter.api.Assertions.assertEquals(response, current);
		}
		org.junit.jupiter.api.Assertions.assertEquals(PasswordResetService.GENERIC_RESPONSE, response);
		verify(emailSender, times(0)).sendPasswordResetLink(anyString(), anyString());
	}

	@Test
	void doesNotCreateOrChangeResetTokenWhenEmailDeliveryFails() throws Exception {
		createUser(true);
		doThrow(new MailSendException("mail unavailable"))
			.when(emailSender).sendPasswordResetLink(eq(email), anyString());

		requestReset(email, remoteAddress);

		org.junit.jupiter.api.Assertions.assertTrue(resetTokenRepository.findAll().isEmpty());
		org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(
			"oldPassword123",
			userRepository.findByCollegeEmail(email).orElseThrow().getPasswordHash()
		));
	}

	@Test
	void requiresCsrfForBothPasswordResetEndpoints() throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s"}
					""".formatted(email)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"token":"invalid","newPassword":"newPassword456","confirmPassword":"newPassword456"}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(forgotRequest(email, remoteAddress))
			.andExpect(status().isAccepted());
	}

	@Test
	void existingSessionsRemainAuthenticatedBecauseNoSessionRegistryIsConfigured() throws Exception {
		User user = createUser(true);
		MockHttpSession existingSession = new MockHttpSession();
		mockMvc.perform(post("/api/auth/login")
				.session(existingSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "oldPassword123")))
			.andExpect(status().isNoContent());

		String token = tokenFromLink(requestResetAndCaptureLink(email, remoteAddress));
		mockMvc.perform(resetRequest(token, "newPassword456", "newPassword456", remoteAddress))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/pages/settings.html").session(existingSession))
			.andExpect(status().isOk());
		org.junit.jupiter.api.Assertions.assertEquals(user.getId(),
			userRepository.findByCollegeEmail(email).orElseThrow().getId());
	}

	private User createUser(boolean verified) {
		return userRepository.saveAndFlush(new User(
			"Reset Test Student",
			email,
			passwordEncoder.encode("oldPassword123"),
			verified
		));
	}

	private String requestReset(String requestEmail, String address) throws Exception {
		mockMvc.perform(forgotRequest(requestEmail, address))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").value(PasswordResetService.GENERIC_RESPONSE));
		return PasswordResetService.GENERIC_RESPONSE;
	}

	private String requestResetAndCaptureLink(String requestEmail, String address) throws Exception {
		mockMvc.perform(forgotRequest(requestEmail, address))
			.andExpect(status().isAccepted());
		ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
		verify(emailSender, atLeastOnce()).sendPasswordResetLink(eq(requestEmail), link.capture());
		return link.getAllValues().getLast();
	}

	private MockHttpServletRequestBuilder forgotRequest(String requestEmail, String address) {
		return post("/api/auth/forgot-password")
			.with(csrf())
			.with(request -> {
				request.setRemoteAddr(address);
				request.addHeader("Host", "attacker.example");
				return request;
			})
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s"}
				""".formatted(requestEmail));
	}

	private MockHttpServletRequestBuilder resetRequest(
		String token,
		String newPassword,
		String confirmPassword,
		String address
	) {
		return post("/api/auth/reset-password")
			.with(csrf())
			.with(request -> {
				request.setRemoteAddr(address);
				return request;
			})
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"token":"%s","newPassword":"%s","confirmPassword":"%s"}
				""".formatted(token, newPassword, confirmPassword));
	}

	private static String tokenFromLink(String link) {
		return link.substring(link.indexOf("#token=") + "#token=".length());
	}

	private static String sha256(String token) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
		);
	}

	private static String loginRequest(String requestEmail, String password) {
		return """
			{"email":"%s","password":"%s"}
			""".formatted(requestEmail, password);
	}
}
