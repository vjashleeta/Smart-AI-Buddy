package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.StudyPlan;
import com.smartai.buddy.entity.StudySession;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.StudyPlanRepository;
import com.smartai.buddy.repository.StudySessionRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.LocalDate;
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
class PlannerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StudyPlanRepository studyPlanRepository;

	@Autowired
	private StudySessionRepository studySessionRepository;

	private String studentEmail;
	private String otherStudentEmail;

	@BeforeEach
	void setUp() {
		studentEmail = "planner-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "planner-other-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Planner Student", studentEmail, "test-hash"));
		userRepository.saveAndFlush(new User("Other Student", otherStudentEmail, "test-hash"));
	}

	@Test
	void savesPlanAndSessionAndReturnsThemAgainAfterReload() throws Exception {
		LocalDate sessionDate = LocalDate.now().plusDays(1);

		mockMvc.perform(post("/api/student/planner/sessions")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest("  Biology  ", sessionDate, 45)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.subject").value("Biology"))
			.andExpect(jsonPath("$.sessionDate").value(sessionDate.toString()))
			.andExpect(jsonPath("$.durationMinutes").value(45));

		StudyPlan plan = studyPlanRepository.findByStudent_Id(
			userRepository.findByCollegeEmail(studentEmail).orElseThrow().getId()
		).orElseThrow();
		StudySession persistedSession = studySessionRepository
			.findAllByPlan_IdOrderBySessionDateAscIdAsc(plan.getId()).getFirst();
		org.junit.jupiter.api.Assertions.assertEquals("Biology", persistedSession.getSubject());
		org.junit.jupiter.api.Assertions.assertEquals(45, persistedSession.getDurationMinutes());

		mockMvc.perform(get("/api/student/planner").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.planId").value(plan.getId()))
			.andExpect(jsonPath("$.sessions.length()").value(1))
			.andExpect(jsonPath("$.sessions[0].subject").value("Biology"))
			.andExpect(jsonPath("$.sessions[0].sessionDate").value(sessionDate.toString()));
	}

	@Test
	void keepsPlansAndSessionsIsolatedByAuthenticatedStudent() throws Exception {
		LocalDate sessionDate = LocalDate.now().plusDays(2);
		mockMvc.perform(post("/api/student/planner/sessions")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest("Physics", sessionDate, 25)))
			.andExpect(status().isCreated());
		Long firstPlanId = studyPlanRepository.findByStudent_Id(
			userRepository.findByCollegeEmail(studentEmail).orElseThrow().getId()
		).orElseThrow().getId();

		mockMvc.perform(get("/api/student/planner").with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.planId").isEmpty())
			.andExpect(jsonPath("$.sessions").isEmpty());

		mockMvc.perform(post("/api/student/planner/sessions")
				.with(user(otherStudentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest("History", sessionDate, 60)))
			.andExpect(status().isCreated());
		Long otherPlanId = studyPlanRepository.findByStudent_Id(
			userRepository.findByCollegeEmail(otherStudentEmail).orElseThrow().getId()
		).orElseThrow().getId();
		org.junit.jupiter.api.Assertions.assertNotEquals(firstPlanId, otherPlanId);

		mockMvc.perform(get("/api/student/planner").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sessions.length()").value(1))
			.andExpect(jsonPath("$.sessions[0].subject").value("Physics"))
			.andExpect(jsonPath("$.planId").value(firstPlanId));

		mockMvc.perform(get("/api/student/planner").with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sessions.length()").value(1))
			.andExpect(jsonPath("$.sessions[0].subject").value("History"))
			.andExpect(jsonPath("$.planId").value(otherPlanId));
	}

	@Test
	void rejectsInvalidSessionsAndRequiresAuthenticationAndCsrf() throws Exception {
		Long studentId = userRepository.findByCollegeEmail(studentEmail).orElseThrow().getId();
		var planBeforeRequests = studyPlanRepository.findByStudent_Id(studentId);

		mockMvc.perform(get("/api/student/planner"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/student/planner/sessions")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest("Biology", LocalDate.now().plusDays(1), 45)))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/student/planner/sessions")
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest("Biology", LocalDate.now().plusDays(1), 45)))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/student/planner/sessions")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionRequest(" ", LocalDate.now().minusDays(1), 0)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.subject").exists())
			.andExpect(jsonPath("$.errors.sessionDate").exists())
			.andExpect(jsonPath("$.errors.durationMinutes").exists());
		assertEquals(planBeforeRequests, studyPlanRepository.findByStudent_Id(studentId));
	}

	@Test
	void recordsAndCorrectsActualTimeWithoutChangingPlannedDuration() throws Exception {
		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(
			userRepository.findByCollegeEmail(studentEmail).orElseThrow()
		));
		StudySession session = studySessionRepository.saveAndFlush(new StudySession(
			plan,
			"Biology",
			LocalDate.now(),
			45
		));

		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.durationMinutes").value(45))
			.andExpect(jsonPath("$.actualDurationMinutes").value(20))
			.andExpect(jsonPath("$.completedAt").isEmpty());

		mockMvc.perform(get("/api/student/planner").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sessions[0].durationMinutes").value(45))
			.andExpect(jsonPath("$.sessions[0].actualDurationMinutes").value(20));

		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":30}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.durationMinutes").value(45))
			.andExpect(jsonPath("$.actualDurationMinutes").value(30))
			.andExpect(jsonPath("$.completedAt").isEmpty());
		assertEquals(45, studySessionRepository.findById(session.getId()).orElseThrow().getDurationMinutes());
		assertEquals(30, studySessionRepository.findById(session.getId()).orElseThrow().getActualDurationMinutes());

		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":30}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actualDurationMinutes").value(30))
			.andExpect(jsonPath("$.completedAt").isNotEmpty());
		var completionTime = studySessionRepository.findById(session.getId()).orElseThrow().getCompletedAt();

		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":30}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actualDurationMinutes").value(30))
			.andExpect(jsonPath("$.completedAt").isNotEmpty());
		assertEquals(
			completionTime,
			studySessionRepository.findById(session.getId()).orElseThrow().getCompletedAt()
		);

		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":25}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actualDurationMinutes").value(25))
			.andExpect(jsonPath("$.completedAt").isNotEmpty());
	}

	@Test
	void actualTimeUpdatesRequireValidInputAndRemainStudentScoped() throws Exception {
		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(
			userRepository.findByCollegeEmail(studentEmail).orElseThrow()
		));
		StudySession session = studySessionRepository.saveAndFlush(new StudySession(
			plan,
			"Biology",
			LocalDate.now(),
			45
		));

		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(otherStudentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(user(otherStudentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":1441}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.actualDurationMinutes").exists());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":46}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Actual study time cannot exceed the planned session duration."));
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":null}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.actualDurationMinutes").exists());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.actualDurationMinutes").exists());
	}

	@Test
	void doesNotAllowFutureSessionsToBeMarkedAsCompleted() throws Exception {
		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(
			userRepository.findByCollegeEmail(studentEmail).orElseThrow()
		));
		StudySession session = studySessionRepository.saveAndFlush(new StudySession(
			plan,
			"Biology",
			LocalDate.now().plusDays(1),
			45
		));
		mockMvc.perform(put("/api/student/planner/sessions/{id}/complete", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":45}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("A future study session cannot be marked as completed."));
		assertNull(studySessionRepository.findById(session.getId()).orElseThrow().getActualDurationMinutes());
		mockMvc.perform(put("/api/student/planner/sessions/{id}/actual-time", session.getId())
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualDurationMinutes\":20}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Actual time cannot be recorded for a future study session."));
		assertNull(studySessionRepository.findById(session.getId()).orElseThrow().getActualDurationMinutes());
	}

	private String sessionRequest(String subject, LocalDate date, int durationMinutes) {
		return """
			{"subject":"%s","sessionDate":"%s","durationMinutes":%d}
			""".formatted(subject, date, durationMinutes);
	}
}
