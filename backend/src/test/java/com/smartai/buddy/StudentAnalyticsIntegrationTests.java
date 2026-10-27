package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.QuizAttempt;
import com.smartai.buddy.entity.QuizAttemptQuestion;
import com.smartai.buddy.entity.QuizConcept;
import com.smartai.buddy.entity.LearningActivity;
import com.smartai.buddy.entity.LearningActivityType;
import com.smartai.buddy.entity.StudyMaterial;
import com.smartai.buddy.entity.StudyPlan;
import com.smartai.buddy.entity.StudySession;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.QuizAttemptQuestionRepository;
import com.smartai.buddy.repository.QuizAttemptRepository;
import com.smartai.buddy.repository.QuizConceptRepository;
import com.smartai.buddy.repository.LearningActivityRepository;
import com.smartai.buddy.repository.StudyMaterialRepository;
import com.smartai.buddy.repository.StudyPlanRepository;
import com.smartai.buddy.repository.StudySessionRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentAnalyticsIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StudyMaterialRepository studyMaterialRepository;

	@Autowired
	private QuizConceptRepository quizConceptRepository;

	@Autowired
	private QuizAttemptRepository quizAttemptRepository;

	@Autowired
	private QuizAttemptQuestionRepository quizAttemptQuestionRepository;

	@Autowired
	private StudyPlanRepository studyPlanRepository;

	@Autowired
	private StudySessionRepository studySessionRepository;

	@Autowired
	private LearningActivityRepository learningActivityRepository;

	@Autowired
	private Clock clock;

	private String studentEmail;
	private String otherStudentEmail;
	private User student;
	private StudyMaterial biologyMaterial;
	private QuizConcept biologyConcept;

	@BeforeEach
	void setUp() {
		studentEmail = "analytics-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "analytics-other-" + UUID.randomUUID() + "@college.edu";
		student = userRepository.saveAndFlush(new User("Analytics Student", studentEmail, "test-hash"));
		userRepository.saveAndFlush(new User("Other Analytics Student", otherStudentEmail, "test-hash"));
		biologyMaterial = studyMaterialRepository.saveAndFlush(
			new StudyMaterial(student, "Biology", "Mitochondria produce energy during cellular respiration.")
		);
		biologyConcept = quizConceptRepository.saveAndFlush(new QuizConcept(
			biologyMaterial,
			1,
			"Mitochondria produce energy during cellular respiration.",
			"Which organelle produces energy?",
			"Mitochondria"
		));
	}

	@Test
	void returnsClearEmptyMetricsForStudentWithoutQuizOrPlanData() throws Exception {
		mockMvc.perform(get("/api/student/dashboard").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(0))
			.andExpect(jsonPath("$.averageScorePercent").doesNotExist())
			.andExpect(jsonPath("$.latestScorePercent").doesNotExist())
			.andExpect(jsonPath("$.recentQuizAttempts").isEmpty())
			.andExpect(jsonPath("$.upcomingPlannedSessions").isEmpty())
			.andExpect(jsonPath("$.completedStudyMinutes").value(0))
			.andExpect(jsonPath("$.currentStreakDays").value(0))
			.andExpect(jsonPath("$.weeklyGoalMinutes").value(360))
			.andExpect(jsonPath("$.weeklyActivity.length()").value(7));

		mockMvc.perform(get("/api/student/performance").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(0))
			.andExpect(jsonPath("$.averageScorePercent").doesNotExist())
			.andExpect(jsonPath("$.latestScorePercent").doesNotExist())
			.andExpect(jsonPath("$.bestScorePercent").doesNotExist())
			.andExpect(jsonPath("$.scoreHistory").isEmpty())
			.andExpect(jsonPath("$.topicPerformance").isEmpty())
			.andExpect(jsonPath("$.completedStudyMinutes").value(0));
	}

	@Test
	void reportsCompletedAttemptsWithCorrectAverageLatestBestAndTopicScores() throws Exception {
		QuizAttempt previous = addAttempt(4, 3, LocalDateTime.now().minusDays(1), true);
		QuizAttempt latest = addAttempt(2, 1, LocalDateTime.now(), true);
		addAttempt(1, 1, LocalDateTime.now(), false);

		mockMvc.perform(get("/api/student/dashboard").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentName").value("Analytics Student"))
			.andExpect(jsonPath("$.completedQuizAttempts").value(2))
			.andExpect(jsonPath("$.averageScorePercent").value(62.5))
			.andExpect(jsonPath("$.latestScorePercent").value(50.0))
			.andExpect(jsonPath("$.latestCorrectAnswers").value(1))
			.andExpect(jsonPath("$.latestTotalQuestions").value(2))
			.andExpect(jsonPath("$.recentQuizAttempts.length()").value(2))
			.andExpect(jsonPath("$.recentQuizAttempts[0].attemptId").value(latest.getId()))
			.andExpect(jsonPath("$.recentQuizAttempts[1].attemptId").value(previous.getId()));

		mockMvc.perform(get("/api/student/performance").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(2))
			.andExpect(jsonPath("$.averageScorePercent").value(62.5))
			.andExpect(jsonPath("$.latestScorePercent").value(50.0))
			.andExpect(jsonPath("$.bestScorePercent").value(75.0))
			.andExpect(jsonPath("$.scoreHistory.length()").value(2))
			.andExpect(jsonPath("$.scoreHistory[0].attemptId").value(latest.getId()))
			.andExpect(jsonPath("$.scoreHistory[0].correctAnswers").value(1))
			.andExpect(jsonPath("$.scoreHistory[0].totalQuestions").value(2))
			.andExpect(jsonPath("$.topicPerformance.length()").value(1))
			.andExpect(jsonPath("$.topicPerformance[0].topic").value("Biology"))
			.andExpect(jsonPath("$.topicPerformance[0].completedAttempts").value(2))
			.andExpect(jsonPath("$.topicPerformance[0].averageScorePercent").value(62.5))
			.andExpect(jsonPath("$.topicPerformance[0].bestScorePercent").value(75.0));
		assertEquals(3, quizAttemptRepository.countByStudent_Id(student.getId()));
	}

	@Test
	void showsOnlyUpcomingPlannerItemsAndNeverCountsPlannedTimeAsCompleted() throws Exception {
		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(student));
		studySessionRepository.saveAndFlush(new StudySession(
			plan,
			"Biology",
			LocalDate.now().plusDays(1),
			45
		));
		StudySession recordedButIncomplete = new StudySession(
			plan,
			"Old planned session",
			LocalDate.now().minusDays(1),
			120
		);
		recordedButIncomplete.recordActualDuration(45, LocalDateTime.now(clock).minusDays(1));
		studySessionRepository.saveAndFlush(recordedButIncomplete);

		mockMvc.perform(get("/api/student/dashboard").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.upcomingPlannedSessionCount").value(1))
			.andExpect(jsonPath("$.upcomingPlannedSessions.length()").value(1))
			.andExpect(jsonPath("$.upcomingPlannedSessions[0].subject").value("Biology"))
			.andExpect(jsonPath("$.completedStudyMinutes").value(0));

		mockMvc.perform(get("/api/student/performance").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.upcomingPlannedSessions.length()").value(1))
			.andExpect(jsonPath("$.completedStudyMinutes").value(0));
	}

	@Test
	void countsActualTimeOnceWhenInteractiveLearningAndCompletedSessionOverlap() throws Exception {
		LocalDateTime now = LocalDateTime.now(clock).withNano(0);
		learningActivityRepository.saveAndFlush(activityInterval(
			student,
			LearningActivityType.AI_BUDDY,
			now.minusMinutes(31),
			now.minusMinutes(16)
		));
		learningActivityRepository.saveAndFlush(activityInterval(
			student,
			LearningActivityType.SOCRATIC_TUTOR,
			now.minusMinutes(27),
			now.minusMinutes(22)
		));

		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(student));
		StudySession completedSession = new StudySession(plan, "Biology", now.toLocalDate(), 45);
		completedSession.recordActualDuration(20, now.minusMinutes(1));
		completedSession.markCompleted(now);
		studySessionRepository.saveAndFlush(completedSession);

		LocalDate yesterday = now.toLocalDate().minusDays(1);
		LocalDate twoDaysAgo = now.toLocalDate().minusDays(2);
		learningActivityRepository.saveAndFlush(activityInterval(
			student,
			LearningActivityType.QUIZ,
			yesterday.atTime(12, 0),
			yesterday.atTime(12, 1)
		));
		learningActivityRepository.saveAndFlush(activityInterval(
			student,
			LearningActivityType.SOCRATIC_TUTOR,
			twoDaysAgo.atTime(12, 0),
			twoDaysAgo.atTime(12, 0).plusSeconds(1)
		));

		User otherStudent = userRepository.findByCollegeEmail(otherStudentEmail).orElseThrow();
		learningActivityRepository.saveAndFlush(activityInterval(
			otherStudent,
			LearningActivityType.QUIZ,
			now.minusHours(2),
			now.minusHours(1)
		));

		int todayIndex = now.getDayOfWeek().getValue() - 1;
		mockMvc.perform(get("/api/student/dashboard").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedStudyMinutes").value(31))
			.andExpect(jsonPath("$.currentStreakDays").value(3))
			.andExpect(jsonPath("$.weeklyGoalMinutes").value(360))
			.andExpect(jsonPath("$.weeklyActivity.length()").value(7))
			.andExpect(jsonPath("$.weeklyActivity[" + todayIndex + "].minutes").value(30));

		mockMvc.perform(get("/api/student/performance").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedStudyMinutes").value(31))
			.andExpect(jsonPath("$.currentStreakDays").value(3));
	}

	@Test
	void resetsStudyStreakAfterAnEntireCalendarDayWithoutActivity() throws Exception {
		User studentWithoutCurrentStreak = userRepository.saveAndFlush(new User(
			"No Streak Student",
			"no-streak-" + UUID.randomUUID() + "@college.edu",
			"test-hash"
		));
		StudyPlan plan = studyPlanRepository.saveAndFlush(new StudyPlan(studentWithoutCurrentStreak));
		StudySession oldSession = new StudySession(
			plan,
			"Biology",
			LocalDate.now(clock).minusDays(8),
			45
		);
		oldSession.recordActualDuration(20, LocalDateTime.now(clock).minusDays(8));
		oldSession.markCompleted(LocalDateTime.now(clock).minusDays(8));
		studySessionRepository.saveAndFlush(oldSession);

		mockMvc.perform(get("/api/student/dashboard").with(user(studentWithoutCurrentStreak.getCollegeEmail())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentStreakDays").value(0))
			.andExpect(jsonPath("$.completedStudyMinutes").value(0));
	}

	private static LearningActivity activityInterval(
		User student,
		LearningActivityType type,
		LocalDateTime start,
		LocalDateTime end
	) {
		LearningActivity activity = new LearningActivity(student, type, start);
		LocalDateTime heartbeat = start;
		while (heartbeat.plusSeconds(15).isBefore(end)) {
			heartbeat = heartbeat.plusSeconds(15);
			activity.heartbeat(heartbeat);
		}
		activity.heartbeat(end);
		return activity;
	}

	@Test
	void protectsAnalyticsEndpointsAndScopesEveryResponseToAuthenticatedStudent() throws Exception {
		addAttempt(1, 1, LocalDateTime.now(), true);
		User otherStudent = userRepository.findByCollegeEmail(otherStudentEmail).orElseThrow();
		StudyMaterial otherMaterial = studyMaterialRepository.saveAndFlush(
			new StudyMaterial(otherStudent, "Physics", "Force changes the motion of an object.")
		);
		QuizConcept otherConcept = quizConceptRepository.saveAndFlush(new QuizConcept(
			otherMaterial,
			1,
			"Force changes the motion of an object.",
			"What changes motion?",
			"Force"
		));
		addAttempt(otherStudent, otherConcept, 1, 0, LocalDateTime.now(), true);

		mockMvc.perform(get("/api/student/dashboard"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/student/performance"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/student/dashboard")
				.param("studentId", String.valueOf(otherStudent.getId()))
				.with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(1))
			.andExpect(jsonPath("$.recentQuizAttempts[0].topic").value("Biology"));
		mockMvc.perform(get("/api/student/performance")
				.param("studentId", String.valueOf(student.getId()))
				.with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(1))
			.andExpect(jsonPath("$.scoreHistory[0].topic").value("Physics"));
	}

	@Test
	void leavesScorePercentageEmptyForCompletedAttemptWithZeroPossibleMarks() throws Exception {
		QuizAttempt invalidAttempt = addAttempt(0, 0, LocalDateTime.now(), true);

		mockMvc.perform(get("/api/student/performance").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedQuizAttempts").value(1))
			.andExpect(jsonPath("$.averageScorePercent").doesNotExist())
			.andExpect(jsonPath("$.latestScorePercent").doesNotExist())
			.andExpect(jsonPath("$.bestScorePercent").doesNotExist())
			.andExpect(jsonPath("$.scoreHistory[0].attemptId").value(invalidAttempt.getId()))
			.andExpect(jsonPath("$.scoreHistory[0].scorePercent").doesNotExist());
	}

	@Test
	void servesDashboardAndPerformancePagesWithLiveDataBindingsAndEmptyStateContainers() throws Exception {
		mockMvc.perform(get("/pages/dashboard.html").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-dashboard-page")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Loading your learning activity...")))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("4<span>h</span> 20")
			)));

		mockMvc.perform(get("/pages/performance.html").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-performance-page")))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("Sample data")
			)));

		mockMvc.perform(get("/js/app.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString(
				"Complete your first quiz to see your progress."
			)))
			.andExpect(content().string(org.hamcrest.Matchers.containsString(
				"fetchAnalytics('/api/student/dashboard')"
			)))
			.andExpect(content().string(org.hamcrest.Matchers.containsString(
				"fetchAnalytics('/api/student/performance')"
			)));
	}

	private QuizAttempt addAttempt(
		int totalQuestions,
		int correctAnswers,
		LocalDateTime completedAt,
		boolean completed
	) {
		return addAttempt(student, biologyConcept, totalQuestions, correctAnswers, completedAt, completed);
	}

	private QuizAttempt addAttempt(
		User owner,
		QuizConcept concept,
		int totalQuestions,
		int correctAnswers,
		LocalDateTime completedAt,
		boolean completed
	) {
		QuizAttempt attempt = new QuizAttempt(owner, totalQuestions);
		for (int index = 0; index < correctAnswers; index++) {
			attempt.recordCorrectAnswer();
		}
		if (completed) {
			attempt.complete(completedAt);
		}
		attempt = quizAttemptRepository.saveAndFlush(attempt);
		quizAttemptQuestionRepository.saveAndFlush(new QuizAttemptQuestion(
			attempt,
			concept,
			1,
			"Question?",
			"A",
			"B",
			"C",
			"D",
			"A",
			"Explanation."
		));
		return attempt;
	}
}
