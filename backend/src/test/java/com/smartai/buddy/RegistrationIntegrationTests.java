package com.smartai.buddy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String email;

	@BeforeEach
	void setUp() {
		email = "registration-test-" + UUID.randomUUID() + "@college.edu";
	}

	@AfterEach
	void cleanUp() {
		userRepository.deleteByCollegeEmail(email);
	}

	@Test
	void allowsRegistrationRequestsFromLocalFrontendOrigins() throws Exception {
		mockMvc.perform(options("/api/auth/register")
				.header(HttpHeaders.ORIGIN, "http://localhost:5500")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"))
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
				containsString("POST")
			));
	}

	@Test
	void rejectsRegistrationPreflightFromUnapprovedOrigins() throws Exception {
		mockMvc.perform(options("/api/auth/register")
				.header(HttpHeaders.ORIGIN, "https://unapproved.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void registersUserAndPersistsOnlyPasswordHash() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.with(csrf())
				.content(registrationRequest("Aanya Sharma", email, "securePass123")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Aanya Sharma"))
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User persistedUser = userRepository.findByCollegeEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(
			"securePass123", persistedUser.getPasswordHash()
		));
		org.junit.jupiter.api.Assertions.assertNotEquals("securePass123", persistedUser.getPasswordHash());
	}

	@Test
	void rejectsDuplicateEmailWithConflictResponse() throws Exception {
		mockMvc.perform(post("/api/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.with(csrf())
			.content(registrationRequest("Aanya Sharma", email, "securePass123")))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.with(csrf())
				.content(registrationRequest("Another Student", email, "anotherPass123")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("An account with this college email already exists."))
			.andExpect(jsonPath("$.errors.email").exists());
	}

	@Test
	void returnsFieldErrorsForInvalidRegistrationInput() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.with(csrf())
				.content(registrationRequest(" ", "not-an-email", "short")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Request details are invalid."))
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	private String registrationRequest(String name, String requestEmail, String password) throws Exception {
		return """
			{"name":"%s","email":"%s","password":"%s"}
			""".formatted(name, requestEmail, password);
	}
}
