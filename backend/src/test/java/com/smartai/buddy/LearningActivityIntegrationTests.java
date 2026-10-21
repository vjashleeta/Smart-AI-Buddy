package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.LearningActivity;
import com.smartai.buddy.entity.LearningActivityType;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.LearningActivityRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningActivityIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LearningActivityRepository activityRepository;

	private String studentEmail;
	private String otherStudentEmail;

	@BeforeEach
	void setUp() {
		studentEmail = "activity-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "activity-other-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Activity Student", studentEmail, "test-hash"));
		userRepository.saveAndFlush(new User("Other Activity Student", otherStudentEmail, "test-hash"));
	}

	@Test
	void createsAndUpdatesServerTimedActivityOnlyForItsAuthenticatedOwner() throws Exception {
		String response = mockMvc.perform(post("/api/student/learning-activity/sessions")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"activityType\":\"AI_BUDDY\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.active").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

		mockMvc.perform(put("/api/student/learning-activity/sessions/{id}/heartbeat", id)
				.with(user(otherStudentEmail))
				.with(csrf()))
			.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/student/learning-activity/sessions/{id}/heartbeat", id)
				.with(user(studentEmail)))
			.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/student/learning-activity/sessions/{id}/heartbeat", id)
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));

		mockMvc.perform(post("/api/student/learning-activity/sessions/{id}/stop", id)
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		assertFalse(activityRepository.findById(id).orElseThrow().isActive());
	}

	@Test
	void requiresValidActivityKindAndAuthenticationForTracking() throws Exception {
		mockMvc.perform(post("/api/student/learning-activity/sessions")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"activityType\":\"AI_BUDDY\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/student/learning-activity/sessions")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"activityType\":\"BACKGROUND_TAB\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void closesStaleActivityHeartbeatWithoutCreditingIdleTime() {
		User student = userRepository.findByCollegeEmail(studentEmail).orElseThrow();
		LocalDateTime startedAt = LocalDateTime.now().minusMinutes(2);
		LearningActivity activity = new LearningActivity(student, LearningActivityType.QUIZ, startedAt);
		assertFalse(activity.heartbeat(startedAt.plusSeconds(31)));
		assertFalse(activity.isActive());
		assertTrue(activity.getEndedAt().isEqual(startedAt));
	}
}
