package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String email;

	@BeforeEach
	void setUp() {
		email = "login-test-" + UUID.randomUUID() + "@college.edu";
	}

	@AfterEach
	void cleanUp() {
		userRepository.deleteByCollegeEmail(email);
	}

	@Test
	void authenticatesRegisteredAccountAndRotatesSessionId() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Login Test","email":"%s","password":"securePass123"}
					""".formatted(email)))
			.andExpect(status().isCreated());

		MockHttpSession session = new MockHttpSession();
		String oldSessionId = session.getId();

		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email.toUpperCase(), "securePass123")))
			.andExpect(status().isNoContent())
			.andReturn();

		assertNotEquals(oldSessionId, session.getId());
		assertTrue(result.getRequest().getSession(false) != null);
	}

	@Test
	void rejectsUnknownEmailAndWrongPasswordWithSameGenericResponse() throws Exception {
		persistUser();

		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest("missing-" + email, "securePass123")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid email or password."))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "incorrectPass123")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid email or password."));
	}

	@Test
	void requiresCsrfTokenForLoginAndLogout() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "securePass123")))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isForbidden());
	}

	@Test
	void providesSessionBoundCsrfTokenForFrontendRequests() throws Exception {
		mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
	}

	@Test
	void unauthenticatedRequestsCannotReachStudentEndpoints() throws Exception {
		mockMvc.perform(get("/api/student/dashboard"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authentication required."));
	}

	@Test
	void protectsDashboardAndLearningPagesButAllowsAuthenticationPages() throws Exception {
		mockMvc.perform(get("/pages/dashboard.html"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/pages/login.html"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/pages/register.html"))
			.andExpect(status().isOk());

		persistUser();
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "securePass123")))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/pages/dashboard.html").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-logout")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"chat.html\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"socratic.html\"")));

		mockMvc.perform(get("/pages/socratic.html").session(session))
			.andExpect(status().isOk());
		mockMvc.perform(get("/pages/chat.html").session(session))
			.andExpect(status().isOk());
	}

	@Test
	void logoutInvalidatesAuthenticatedSession() throws Exception {
		persistUser();
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, "securePass123")))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/student/dashboard").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(0));

		mockMvc.perform(post("/api/auth/logout")
				.session(session)
				.with(csrf()))
			.andExpect(status().isNoContent());

		assertTrue(session.isInvalid());
		mockMvc.perform(get("/api/student/dashboard"))
			.andExpect(status().isUnauthorized());
	}

	private String loginRequest(String requestEmail, String password) {
		return """
			{"email":"%s","password":"%s"}
			""".formatted(requestEmail, password);
	}

	private void persistUser() {
		userRepository.saveAndFlush(new User("Login Test", email, passwordEncoder.encode("securePass123")));
	}
}
