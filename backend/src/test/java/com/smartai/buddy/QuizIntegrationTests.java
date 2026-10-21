package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.dto.QuizAttemptResponse;
import com.smartai.buddy.dto.QuizMaterialResponse;
import com.smartai.buddy.dto.QuizOverviewResponse;
import com.smartai.buddy.entity.QuizAttempt;
import com.smartai.buddy.entity.QuizAttemptQuestion;
import com.smartai.buddy.entity.QuizConcept;
import com.smartai.buddy.entity.StudyMaterial;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.QuizAnswerRepository;
import com.smartai.buddy.repository.QuizAttemptRepository;
import com.smartai.buddy.repository.QuizAttemptQuestionRepository;
import com.smartai.buddy.repository.QuizConceptRepository;
import com.smartai.buddy.repository.StudyMaterialRepository;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.ConceptInput;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.GeneratedOption;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.GeneratedQuestion;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
	"smart-ai-buddy.quiz.mastery-consecutive-correct=2",
	"smart-ai-buddy.quiz.questions-per-attempt=2"
})
@AutoConfigureMockMvc
@Transactional
class QuizIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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
	private QuizAnswerRepository quizAnswerRepository;

	@MockitoBean
	private GeminiQuizQuestionGenerator quizQuestionGenerator;

	private String studentEmail;
	private String otherStudentEmail;

	@BeforeEach
	void setUp() {
		studentEmail = "quiz-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "quiz-other-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Quiz Student", studentEmail, "test-hash"));
		userRepository.saveAndFlush(new User("Other Student", otherStudentEmail, "test-hash"));
		when(quizQuestionGenerator.generate(anyString(), anyString(), anyList()))
			.thenAnswer(invocation -> {
				List<ConceptInput> concepts = invocation.getArgument(2);
				return concepts.stream()
					.map(concept -> new GeneratedQuestion(
						concept.conceptId(),
						"Which term completes this source-grounded statement: "
							+ concept.sourceSentence().replace(concept.expectedAnswer(), "________") + "?",
						List.of(
							new GeneratedOption("A", concept.expectedAnswer()),
							new GeneratedOption("B", "Alternative one"),
							new GeneratedOption("C", "Alternative two"),
							new GeneratedOption("D", "Alternative three")
						),
						"A",
						concept.sourceSentence(),
						"This term is directly associated with the described process."
					))
					.toList();
			});
	}

	@Test
	void generatesQuestionsFromSavedSourceAndPersistsAnswersAndFinalScore() throws Exception {
		String sourceText = "Mitochondria produce energy during cellular respiration. "
			+ "Ribosomes synthesize proteins in cells.";
		QuizMaterialResponse material = addMaterial("Cell biology", sourceText);
		assertEquals(2, material.conceptCount());

		QuizAttemptResponse attempt = startAttempt(studentEmail, material.id());
		assertEquals(2, attempt.totalQuestions());
		assertEquals(4, attempt.questions().getFirst().options().size());
		assertEquals("A", attempt.questions().getFirst().options().getFirst().label());
		assertTrue(attempt.questions().getFirst().prompt().contains("________ produce energy"));
		assertTrue(attempt.questions().get(1).prompt().contains("________ synthesize proteins"));
		assertFalse(attempt.questions().getFirst().prompt().contains("Mitochondria"));

		QuizConcept firstConcept = quizConceptRepository.findById(
			attempt.questions().getFirst().conceptId()
		).orElseThrow();
		assertEquals("Mitochondria produce energy during cellular respiration.", firstConcept.getSourceSentence());
		assertEquals("Mitochondria", firstConcept.getExpectedAnswer());

		Long firstConceptId = attempt.questions().getFirst().conceptId();
		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials[0].id").value(material.id()));
		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(firstConceptId, "incorrect")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.correct").value(false))
			.andExpect(jsonPath("$.correctAnswer").value("Mitochondria"))
			.andExpect(jsonPath("$.correctOption").value("A"))
			.andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers
				.containsString("The supplied material states: \"Mitochondria produce energy during cellular respiration.\"")))
			.andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers
				.containsString("Additional context, which may go beyond the supplied material:")))
			.andExpect(jsonPath("$.correctAnswers").value(0))
			.andExpect(jsonPath("$.completed").value(false));

		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(firstConceptId, "Mitochondria")))
			.andExpect(status().isConflict());

		Long secondConceptId = attempt.questions().get(1).conceptId();
		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(secondConceptId, "RIBOSOMES.")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.correct").value(true))
			.andExpect(jsonPath("$.correctAnswers").value(1))
			.andExpect(jsonPath("$.totalQuestions").value(2))
			.andExpect(jsonPath("$.completed").value(true));

		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(secondConceptId, "Ribosomes")))
			.andExpect(status().isConflict());

		QuizAttempt persistedAttempt = quizAttemptRepository.findById(attempt.attemptId()).orElseThrow();
		assertTrue(persistedAttempt.isCompleted());
		assertTrue(persistedAttempt.getCreatedAt() != null);
		assertTrue(persistedAttempt.getCompletedAt() != null);
		assertEquals(1, persistedAttempt.getCorrectAnswers());
		assertEquals(2, quizAnswerRepository.countByAttemptQuestion_Attempt_Id(attempt.attemptId()));
		List<QuizAttemptQuestion> persistedQuestions =
			quizAttemptQuestionRepository.findAllByAttempt_IdOrderByQuestionPositionAsc(attempt.attemptId());
		assertEquals("A", persistedQuestions.getFirst().getCorrectOption());
		assertEquals("Mitochondria", persistedQuestions.getFirst().getOption("A"));
		assertEquals(material.id(), persistedQuestions.getFirst().getConcept().getMaterial().getId());
		assertTrue(persistedQuestions.getFirst().getExplanation().contains("Additional context"));
		assertEquals(4, List.of("A", "B", "C", "D").stream()
			.filter(label -> persistedQuestions.getFirst().getOption(label) != null)
			.count());
		assertEquals(java.util.Set.of("A", "B"), quizAnswerRepository
			.findAllByAttemptQuestion_Attempt_IdOrderByAnsweredAtAsc(attempt.attemptId())
			.stream()
			.map(answer -> answer.getSubmittedAnswer())
			.collect(java.util.stream.Collectors.toSet()));
		StudyMaterial persistedMaterial = studyMaterialRepository
			.findAllByStudent_IdOrderByCreatedAtDesc(studentId(studentEmail))
			.getFirst();
		assertEquals(2, persistedMaterial.getSourceText().split("\\. ").length);
		QuizOverviewResponse overview = objectMapper.readValue(
			mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxTopicCharacters").value(200))
				.andExpect(jsonPath("$.maxPdfBytes").value(20 * 1024 * 1024))
				.andExpect(jsonPath("$.maxPdfPages").value(300))
				.andReturn().getResponse().getContentAsString(),
			QuizOverviewResponse.class
		);
		assertEquals(1, overview.inProgress());
		assertEquals(1, overview.needsRevision());
	}

	@Test
	void rejectsFreeTextAndForgedScoresWithoutSavingAnAnswer() throws Exception {
		QuizMaterialResponse material = addMaterial(
			"Cell biology",
			"Mitochondria produce energy during cellular respiration."
		);
		QuizAttemptResponse attempt = startAttempt(studentEmail, material.id());
		Long conceptId = attempt.questions().getFirst().conceptId();

		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"conceptId":%d,"answer":"Mitochondria","studentId":9999,"score":9999}
					""".formatted(conceptId)))
			.andExpect(status().isBadRequest());

		assertEquals(0, quizAnswerRepository.countByAttemptQuestion_Attempt_Id(attempt.attemptId()));
		QuizAttempt persistedAttempt = quizAttemptRepository.findById(attempt.attemptId()).orElseThrow();
		assertFalse(persistedAttempt.isCompleted());
		assertEquals(0, persistedAttempt.getCorrectAnswers());
	}

	@Test
	void providerFailureDoesNotCreateAnIncompleteAttempt() throws Exception {
		QuizMaterialResponse material = addMaterial(
			"Cell biology",
			"Mitochondria produce energy during cellular respiration."
		);
		long attemptsBefore = quizAttemptRepository.count();
		when(quizQuestionGenerator.generate(anyString(), anyString(), anyList()))
			.thenThrow(new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.BAD_GATEWAY,
				"Gemini returned an invalid quiz question response."
			));

		mockMvc.perform(post("/api/student/quiz/materials/" + material.id() + "/attempts")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadGateway());

		assertEquals(attemptsBefore, quizAttemptRepository.count());
	}

	@Test
	void selectsUntestedThenNeedsRevisionConceptsForNextQuiz() throws Exception {
		QuizMaterialResponse material = addMaterial(
			"Biology",
			"Mitochondria produce energy during cellular respiration. "
				+ "Ribosomes synthesize proteins in cells. "
				+ "Chloroplasts convert light into chemical energy."
		);
		QuizAttemptResponse firstAttempt = startAttempt(studentEmail, material.id());
		Long missedConceptId = firstAttempt.questions().getFirst().conceptId();

		mockMvc.perform(post(answerPath(firstAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(missedConceptId, "wrong")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conceptStatus").value("NEEDS_REVISION"));

		mockMvc.perform(post(answerPath(firstAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(firstAttempt.questions().get(1).conceptId(), "Ribosomes")))
			.andExpect(status().isOk());

		QuizAttemptResponse nextAttempt = startAttempt(studentEmail, material.id());
		Long notYetTestedConceptId = quizConceptRepository
			.findAllByMaterial_Student_IdOrderByMaterial_CreatedAtAscSequenceNumberAsc(
				userRepository.findByCollegeEmail(studentEmail).orElseThrow().getId()
			)
			.get(2)
			.getId();
		assertEquals(notYetTestedConceptId, nextAttempt.questions().getFirst().conceptId());
		assertEquals(missedConceptId, nextAttempt.questions().get(1).conceptId());
	}

	@Test
	void appliesConsecutiveCorrectMasteryAndAllowsLaterRevision() throws Exception {
		QuizMaterialResponse material = addMaterial("Cell biology", "Mitochondria produce energy during cellular respiration.");
		Long conceptId = quizConceptRepository
			.findAllByMaterial_Student_IdOrderByMaterial_CreatedAtAscSequenceNumberAsc(studentId(studentEmail))
			.getFirst()
			.getId();

		QuizAttemptResponse firstAttempt = startAttempt(studentEmail, material.id());
		mockMvc.perform(post(answerPath(firstAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(conceptId, "wrong")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conceptStatus").value("NEEDS_REVISION"))
			.andExpect(jsonPath("$.consecutiveCorrect").value(0));

		QuizAttemptResponse secondAttempt = startAttempt(studentEmail, material.id());
		mockMvc.perform(post(answerPath(secondAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(conceptId, "Mitochondria")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conceptStatus").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.consecutiveCorrect").value(1));

		QuizAttemptResponse thirdAttempt = startAttempt(studentEmail, material.id());
		mockMvc.perform(post(answerPath(thirdAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(conceptId, "mitochondria")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conceptStatus").value("MASTERED"))
			.andExpect(jsonPath("$.consecutiveCorrect").value(2));

		QuizAttemptResponse revisionAttempt = startAttempt(studentEmail, material.id());
		mockMvc.perform(post(answerPath(revisionAttempt.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(conceptId, "wrong")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conceptStatus").value("NEEDS_REVISION"))
			.andExpect(jsonPath("$.consecutiveCorrect").value(0));
		assertEquals(4, quizAttemptRepository.countByStudent_Id(studentId(studentEmail)));
	}

	@Test
	void acceptsConfiguredTopicLimitAndRejectsTopicsBeyondIt() throws Exception {
		String topicAtLimit = "T".repeat(200);
		addMaterial(topicAtLimit, "Mitochondria produce energy during cellular respiration.");

		mockMvc.perform(post("/api/student/quiz/materials")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(materialRequest("T".repeat(201), "Ribosomes synthesize proteins in cells.")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Enter a topic of no more than 200 characters."));

		assertEquals(1, studyMaterialRepository.countByStudent_Id(studentId(studentEmail)));
	}

	@Test
	void enforcesAuthenticationCsrfAndStudentIsolation() throws Exception {
		long materialsBefore = studyMaterialRepository.countByStudent_Id(studentId(studentEmail));
		mockMvc.perform(get("/api/student/quiz"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/student/quiz/materials")
				.with(user(studentEmail))
				.contentType(MediaType.APPLICATION_JSON)
				.content(materialRequest("Biology", "Mitochondria produce energy during cellular respiration.")))
			.andExpect(status().isForbidden());

		QuizMaterialResponse material = addMaterial(
			"Biology",
			"Mitochondria produce energy during cellular respiration."
		);
		QuizAttemptResponse attempt = startAttempt(studentEmail, material.id());

		mockMvc.perform(get("/api/student/quiz").with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials").isEmpty())
			.andExpect(jsonPath("$.concepts").isEmpty());

		mockMvc.perform(post("/api/student/quiz/materials/" + material.id() + "/attempts")
				.with(user(otherStudentEmail)).with(csrf()))
			.andExpect(status().isNotFound());

		mockMvc.perform(post(answerPath(attempt.attemptId()))
				.with(user(otherStudentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(attempt.questions().getFirst().conceptId(), "Mitochondria")))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/student/quiz/materials")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(materialRequest("Unusable", "This is that which has been with the.")))
			.andExpect(status().isBadRequest());
		assertEquals(materialsBefore + 1, studyMaterialRepository.countByStudent_Id(studentId(studentEmail)));
		assertEquals(1, quizConceptRepository.countByMaterial_Id(material.id()));
	}

	private QuizMaterialResponse addMaterial(String topic, String sourceText) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/student/quiz/materials")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(materialRequest(topic, sourceText)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), QuizMaterialResponse.class);
	}

	private QuizAttemptResponse startAttempt(String email, Long materialId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/student/quiz/materials/" + materialId + "/attempts")
				.with(user(email))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), QuizAttemptResponse.class);
	}

	private String materialRequest(String topic, String sourceText) {
		return """
			{"topic":"%s","sourceText":"%s"}
			""".formatted(topic, sourceText);
	}

	private String answerRequest(Long conceptId, String answer) {
		String option = answer.equalsIgnoreCase("wrong") || answer.equalsIgnoreCase("incorrect") ? "B" : "A";
		return """
			{"conceptId":%d,"answer":"%s"}
			""".formatted(conceptId, option);
	}

	private String answerPath(Long attemptId) {
		return "/api/student/quiz/attempts/" + attemptId + "/answers";
	}

	private Long studentId(String email) {
		return userRepository.findByCollegeEmail(email).orElseThrow().getId();
	}

	@Test
	void quizzesUseOnlyTheSelectedMaterialAndRequireAnExplicitSelection() throws Exception {
		QuizMaterialResponse biology = addMaterial(
			"Biology",
			"Mitochondria produce energy during cellular respiration. Ribosomes synthesize proteins in cells."
		);
		addMaterial(
			"Chemistry",
			"Atoms contain protons and neutrons in the nucleus. Electrons occupy energy levels around the nucleus."
		);

		mockMvc.perform(post("/api/student/quiz/attempts").with(user(studentEmail)).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Select a study material before starting a quiz."));

		QuizAttemptResponse attempt = startAttempt(studentEmail, biology.id());
		assertEquals(2, attempt.questions().size());
		assertTrue(attempt.questions().stream().allMatch(question ->
			question.prompt().contains("energy during cellular respiration")
				|| question.prompt().contains("synthesize proteins")
		));
		assertTrue(attempt.questions().stream().noneMatch(question ->
			question.prompt().contains("protons and neutrons")
				|| question.prompt().contains("energy levels")
		));
	}

	@Test
	void removingPastedMaterialPreservesHistoryAndHidesItFromFutureQuizzes() throws Exception {
		QuizMaterialResponse removed = addMaterial(
			"Biology",
			"Mitochondria produce energy during cellular respiration."
		);
		QuizMaterialResponse retained = addMaterial(
			"Chemistry",
			"Atoms contain protons and neutrons in the nucleus."
		);
		QuizAttemptResponse history = startAttempt(studentEmail, removed.id());
		Long conceptId = history.questions().getFirst().conceptId();
		mockMvc.perform(post(answerPath(history.attemptId()))
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(answerRequest(conceptId, "Mitochondria")))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/student/quiz/materials/" + removed.id())
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isNoContent());

		assertTrue(studyMaterialRepository.findById(removed.id()).orElseThrow().isDeleted());
		assertEquals(1, quizConceptRepository.countByMaterial_Id(removed.id()));
		assertTrue(quizAttemptRepository.findById(history.attemptId()).orElseThrow().isCompleted());
		assertEquals(1, quizAnswerRepository.countByAttemptQuestion_Attempt_Id(history.attemptId()));
		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials.length()").value(1))
			.andExpect(jsonPath("$.materials[0].id").value(retained.id()))
			.andExpect(jsonPath("$.concepts.length()").value(1))
			.andExpect(jsonPath("$.concepts[0].materialId").value(retained.id()));

		QuizAttemptResponse next = startAttempt(studentEmail, retained.id());
		assertTrue(next.questions().stream().allMatch(question ->
			question.prompt().contains("protons")
		));
		mockMvc.perform(post("/api/student/quiz/materials/" + removed.id() + "/attempts")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void cannotRemoveAnotherStudentsMaterialAndDeleteRequiresAuthenticationAndCsrf() throws Exception {
		QuizMaterialResponse material = addMaterial(
			"Biology",
			"Mitochondria produce energy during cellular respiration."
		);
		mockMvc.perform(delete("/api/student/quiz/materials/" + material.id()).with(csrf()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/student/quiz/materials/" + material.id()).with(user(studentEmail)))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/student/quiz/materials/" + material.id())
				.with(user(otherStudentEmail))
				.with(csrf()))
			.andExpect(status().isNotFound());

		assertFalse(studyMaterialRepository.findById(material.id()).orElseThrow().isDeleted());
	}
}
