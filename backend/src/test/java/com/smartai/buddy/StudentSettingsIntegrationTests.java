package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentSettingsIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String studentEmail;
	private String otherStudentEmail;
	private User student;
	private User otherStudent;

	@BeforeEach
	void setUp() {
		studentEmail = "settings-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "settings-other-" + UUID.randomUUID() + "@college.edu";
		student = userRepository.saveAndFlush(new User(
			"Settings Student",
			studentEmail,
			passwordEncoder.encode("currentPass123")
		));
		otherStudent = userRepository.saveAndFlush(new User(
			"Other Student",
			otherStudentEmail,
			passwordEncoder.encode("otherPass123")
		));
	}

	@Test
	void returnsAuthenticatedProfileWithoutExposingPasswordHash() throws Exception {
		mockMvc.perform(get("/api/student/settings/profile").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Settings Student"))
			.andExpect(jsonPath("$.email").value(studentEmail))
			.andExpect(jsonPath("$.id").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/student/settings/profile"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updatesOnlyAuthenticatedStudentsNameAndRejectsInvalidNames() throws Exception {
		mockMvc.perform(put("/api/student/settings/profile")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"  Updated Student  ","email":"%s","studentId":%d}
					""".formatted(otherStudentEmail, otherStudent.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Student"))
			.andExpect(jsonPath("$.email").value(studentEmail))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		assertEquals("Updated Student", userRepository.findById(student.getId()).orElseThrow().getName());
		assertEquals("Other Student", userRepository.findById(otherStudent.getId()).orElseThrow().getName());

		mockMvc.perform(put("/api/student/settings/profile")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":" "}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());

		mockMvc.perform(put("/api/student/settings/profile")
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Missing CSRF"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void changesPasswordOnlyAfterVerifyingCurrentPasswordAndPersistsHash() throws Exception {
		mockMvc.perform(post("/api/student/settings/password")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"incorrectPass","newPassword":"updatedPass123"}
					"""))
			.andExpect(status().isBadRequest());
		assertTrue(passwordEncoder.matches(
			"currentPass123",
			userRepository.findById(student.getId()).orElseThrow().getPasswordHash()
		));

		mockMvc.perform(post("/api/student/settings/password")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"currentPass123","newPassword":"updatedPass123"}
					"""))
			.andExpect(status().isNoContent());

		String updatedHash = userRepository.findById(student.getId()).orElseThrow().getPasswordHash();
		assertTrue(passwordEncoder.matches("updatedPass123", updatedHash));
		assertFalse(passwordEncoder.matches("currentPass123", updatedHash));
		assertTrue(passwordEncoder.matches(
			"otherPass123",
			userRepository.findById(otherStudent.getId()).orElseThrow().getPasswordHash()
		));

		mockMvc.perform(post("/api/student/settings/password")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"updatedPass123","newPassword":"short"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.newPassword").exists());
	}

	@Test
	void requiresAuthenticationAndCsrfForPasswordChange() throws Exception {
		mockMvc.perform(post("/api/student/settings/password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"currentPass123","newPassword":"updatedPass123"}
					"""))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/student/settings/password")
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"currentPass123","newPassword":"updatedPass123"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void allowsCrossOriginPreflightForAuthenticatedProfileUpdates() throws Exception {
		mockMvc.perform(options("/api/student/settings/profile")
				.header("Origin", "http://localhost:5500")
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("Access-Control-Allow-Origin", "http://localhost:5500"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PUT")));
	}
}
