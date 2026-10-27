package com.smartai.buddy;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.EmailVerificationCodeRepository;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.VerificationEmailSender;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationIntegrationTests {

	private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailVerificationCodeRepository verificationCodeRepository;

	@Autowired
	private DataSource dataSource;

	@MockitoBean
	private VerificationEmailSender verificationEmailSender;

	@MockitoBean
	private Clock clock;

	private String email;

	@BeforeEach
	void setUp() {
		email = "verification-test-" + UUID.randomUUID() + "@college.edu";
		when(clock.instant()).thenReturn(START);
	}

	@Test
	void mapsPurposeAsVarchar32() throws Exception {
		try (Connection connection = dataSource.getConnection();
			 ResultSet columns = connection.getMetaData().getColumns(
				 connection.getCatalog(),
				 null,
				 "EMAIL_VERIFICATION_CODES",
				 "PURPOSE"
			 )) {
			org.junit.jupiter.api.Assertions.assertTrue(columns.next());
			org.junit.jupiter.api.Assertions.assertEquals(Types.VARCHAR, columns.getInt("DATA_TYPE"));
			org.junit.jupiter.api.Assertions.assertEquals(32, columns.getInt("COLUMN_SIZE"));
			org.junit.jupiter.api.Assertions.assertFalse(columns.next());
		}
	}

	@AfterEach
	void cleanUp() {
		userRepository.findByCollegeEmail(email)
			.ifPresent(user -> verificationCodeRepository.deleteByUser_Id(user.getId()));
		userRepository.deleteByCollegeEmail(email);
	}

	@Test
	void verifiesValidCodeAndRejectsItsReuse() throws Exception {
		String code = registerAndCaptureCode();

		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(code)))
			.andExpect(status().isNoContent());

		User verifiedUser = userRepository.findByCollegeEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertTrue(verifiedUser.isEmailVerified());

		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(code)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The verification code is invalid or expired."));
	}

	@Test
	void rejectsInvalidAndExpiredCodes() throws Exception {
		String code = registerAndCaptureCode();
		String invalidCode = code.equals("00000000") ? "00000001" : "00000000";

		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(invalidCode)))
			.andExpect(status().isBadRequest());

		when(clock.instant()).thenReturn(START.plusSeconds(11 * 60L));
		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(code)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The verification code is invalid or expired."));
	}

	@Test
	void limitsVerificationAttempts() throws Exception {
		String code = registerAndCaptureCode();
		String invalidCode = code.equals("00000000") ? "00000001" : "00000000";

		for (int attempt = 1; attempt <= 5; attempt++) {
			int expectedStatus = attempt == 5 ? 429 : 400;
			mockMvc.perform(post("/api/auth/verify-email")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(verificationRequest(invalidCode)))
				.andExpect(status().is(expectedStatus));
		}

		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(code)))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void limitsResendFrequencyAndHourlySendCount() throws Exception {
		registerAndCaptureCode();

		mockMvc.perform(resendRequest())
			.andExpect(status().isTooManyRequests());

		when(clock.instant()).thenReturn(START.plusSeconds(61));
		doNothing().when(verificationEmailSender).sendVerificationCode(eq(email), anyString());
		mockMvc.perform(resendRequest())
			.andExpect(status().isNoContent());

		when(clock.instant()).thenReturn(START.plusSeconds(122));
		mockMvc.perform(resendRequest())
			.andExpect(status().isNoContent());

		when(clock.instant()).thenReturn(START.plusSeconds(183));
		mockMvc.perform(resendRequest())
			.andExpect(status().isTooManyRequests());

		verify(verificationEmailSender, times(3)).sendVerificationCode(eq(email), anyString());
	}

	@Test
	void deliveryFailureLeavesAccountUnverifiedAndAllowsRetry() throws Exception {
		doThrow(new MailSendException("mail unavailable"))
			.when(verificationEmailSender).sendVerificationCode(eq(email), anyString());

		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Verification Test","email":"%s","password":"securePass123"}
					""".formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.verificationEmailSent").value(false));

		org.junit.jupiter.api.Assertions.assertFalse(
			userRepository.findByCollegeEmail(email).orElseThrow().isEmailVerified()
		);

		when(clock.instant()).thenReturn(START.plusSeconds(61));
		doNothing().when(verificationEmailSender).sendVerificationCode(eq(email), anyString());
		mockMvc.perform(resendRequest())
			.andExpect(status().isNoContent());
		verify(verificationEmailSender, times(2)).sendVerificationCode(eq(email), anyString());
	}

	@Test
	void failedResendKeepsPreviouslyDeliveredCodeUsable() throws Exception {
		String originalCode = registerAndCaptureCode();
		when(clock.instant()).thenReturn(START.plusSeconds(61));
		doThrow(new MailSendException("mail unavailable"))
			.when(verificationEmailSender).sendVerificationCode(eq(email), anyString());

		mockMvc.perform(resendRequest())
			.andExpect(status().isServiceUnavailable());

		mockMvc.perform(post("/api/auth/verify-email")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(verificationRequest(originalCode)))
			.andExpect(status().isNoContent());
	}

	private String registerAndCaptureCode() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Verification Test","email":"%s","password":"securePass123"}
					""".formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.verificationEmailSent").value(true));

		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(verificationEmailSender).sendVerificationCode(eq(email), code.capture());
		org.junit.jupiter.api.Assertions.assertTrue(code.getValue().matches("\\d{8}"));
		return code.getValue();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resendRequest() {
		return post("/api/auth/resend-verification")
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s"}
				""".formatted(email));
	}

	private String verificationRequest(String code) {
		return """
			{"email":"%s","code":"%s"}
			""".formatted(email, code);
	}
}
