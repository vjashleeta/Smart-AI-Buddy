package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.dto.QuizAttemptResponse;
import com.smartai.buddy.dto.QuizMaterialResponse;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.ConceptInput;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.GeneratedOption;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.GeneratedQuestion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

@SpringBootTest(properties = "smart-ai-buddy.quiz.mastery-consecutive-correct=2")
@AutoConfigureMockMvc
@Transactional
class ResourceRecommendationIntegrationTests {

	private static final String RECOMMENDATIONS_URL = "/api/student/resources/recommendations";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private GeminiQuizQuestionGenerator quizQuestionGenerator;

	private String studentEmail;
	private String otherStudentEmail;

	@BeforeEach
	void setUp() {
		studentEmail = "resource-recommendation-" + UUID.randomUUID() + "@college.edu";
		otherStudentEmail = "resource-recommendation-other-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Recommendation Student", studentEmail, "test-hash"));
		userRepository.saveAndFlush(new User("Other Recommendation Student", otherStudentEmail, "test-hash"));
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
	void recommendsLowMasteryTopicsAfterSufficientEvidenceAndOmitsMasteredTopics() throws Exception {
		QuizMaterialResponse weakMaterial = addMaterial(
			studentEmail,
			"Cell biology",
			"Mitochondria produce energy during cellular respiration."
		);
		answerConcept(weakMaterial.id(), "wrong");
		answerConcept(weakMaterial.id(), "wrong");

		QuizMaterialResponse masteredMaterial = addMaterial(
			studentEmail,
			"Statistics",
			"Probability measures the likelihood of an event."
		);
		answerConcept(masteredMaterial.id(), "Probability");
		answerConcept(masteredMaterial.id(), "Probability");

		mockMvc.perform(get(RECOMMENDATIONS_URL).with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$..topic").value(org.hamcrest.Matchers.hasItem("Cell biology")))
			.andExpect(jsonPath("$..topic").value(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.hasItem("Statistics")
			)))
			.andExpect(jsonPath("$[0].source").value("QUIZ_PROGRESS"))
			.andExpect(jsonPath("$[0].explanation").value(
				org.hamcrest.Matchers.containsString("at least 2 quiz answers")
			))
			.andExpect(jsonPath("$[0].resources.length()").value(2))
			.andExpect(jsonPath("$[0].resources[0].type").value("LEARNING_PAGE"))
			.andExpect(jsonPath("$[0].resources[0].url").value(
				"https://openstax.org/books/biology-2e/pages/7-1-energy-in-living-systems"
			));
	}

	@Test
	void oneIncorrectAnswerDoesNotLabelTopicWeak() throws Exception {
		QuizMaterialResponse material = addMaterial(
			studentEmail,
			"Cell biology",
			"Mitochondria produce energy during cellular respiration."
		);
		answerConcept(material.id(), "wrong");

		mockMvc.perform(get(RECOMMENDATIONS_URL).with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void recommendsStudentReportedTopicsAndClearlyReportsMissingMatches() throws Exception {
		mockMvc.perform(post(RECOMMENDATIONS_URL)
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\":\"probability\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topic").value("probability"))
			.andExpect(jsonPath("$.source").value("STUDENT_REPORTED"))
			.andExpect(jsonPath("$.resources.length()").value(3))
			.andExpect(jsonPath("$.resources[0].type").value("NOTES"))
			.andExpect(jsonPath("$.resources[0].url").value(
				"https://openstax.org/books/introductory-statistics/pages/3-introduction"
			));

		mockMvc.perform(post(RECOMMENDATIONS_URL)
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\":\"Astrophysics of distant quasars\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topic").value("Astrophysics of distant quasars"))
			.andExpect(jsonPath("$.resources.length()").value(2))
			.andExpect(jsonPath("$.resources[0].type").value("EDUCATIONAL_SEARCH"))
			.andExpect(jsonPath("$.resources[0].title").value(
				org.hamcrest.Matchers.containsString("Educational search results")
			));
	}

	@Test
	void recommendationsUseOnlyTheAuthenticatedStudentsQuizProgress() throws Exception {
		QuizMaterialResponse material = addMaterial(
			studentEmail,
			"Cell biology",
			"Mitochondria produce energy during cellular respiration."
		);
		answerConcept(material.id(), "wrong");
		answerConcept(material.id(), "wrong");

		mockMvc.perform(get(RECOMMENDATIONS_URL).with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].topic").value("Cell biology"));
		mockMvc.perform(get(RECOMMENDATIONS_URL).with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get(RECOMMENDATIONS_URL))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void savedMaterialsForRecentResourcesAreEmptyUntilSavedAndScopedToTheAuthenticatedStudent() throws Exception {
		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials").isEmpty());

		addMaterial(studentEmail, "Cell biology", "Mitochondria produce energy during cellular respiration.");
		addMaterial(otherStudentEmail, "Data structures", "A queue follows the first in first out principle.");

		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials.length()").value(1))
			.andExpect(jsonPath("$.materials[0].topic").value("Cell biology"))
			.andExpect(jsonPath("$.materials[0].createdAt").exists());

		mockMvc.perform(get("/api/student/quiz").with(user(otherStudentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials.length()").value(1))
			.andExpect(jsonPath("$.materials[0].topic").value("Data structures"));
	}

	@Test
	void resourcesPageContainsNoHardcodedDemoRecentResources() throws Exception {
		String page = mockMvc.perform(get("/pages/resources.html").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertFalse(page.contains("Probability and Statistics · Chapter 4"));
		assertFalse(page.contains("Stacks and queues summary"));
		assertFalse(page.contains("Introduction to supervised learning"));
		assertFalse(page.contains("Q summary introduction to FIFO"));

		String appJs = mockMvc.perform(get("/js/app.js").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(appJs.contains(
			"Your recent resources will appear here after you save study materials."
		));
	}

	@Test
	void topicAliasesMatchWordingOrderCaseAndSpacingVariations() throws Exception {
		for (String topic : List.of(
			"C programming functions",
			"functions in C",
			"user-defined functions in C"
		)) {
			mockMvc.perform(post(RECOMMENDATIONS_URL)
					.with(user(studentEmail))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(java.util.Map.of("topic", topic))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.topic").value(topic))
				.andExpect(jsonPath("$.resources.length()").value(2))
				.andExpect(jsonPath("$.resources[0].url").value("https://www.learn-c.org/en/Functions"));
		}

		mockMvc.perform(post(RECOMMENDATIONS_URL)
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\":\"  JAVA   classes and objects \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resources[0].url").value("https://dev.java/learn/"));
	}

	@Test
	void unknownSpecificTopicGetsClearlyLabeledEducationalSearchFallbackAndVagueTopicDoesNot() throws Exception {
		mockMvc.perform(post(RECOMMENDATIONS_URL)
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\":\"quantum error correction\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resources.length()").value(2))
			.andExpect(jsonPath("$.resources[0].type").value("EDUCATIONAL_SEARCH"))
			.andExpect(jsonPath("$.resources[0].title").value(
				org.hamcrest.Matchers.containsString("Educational search results")
			))
			.andExpect(jsonPath("$.resources[1].type").value("VIDEO_SEARCH"))
			.andExpect(jsonPath("$.resources[1].title").value(
				org.hamcrest.Matchers.containsString("video search")
			));

		mockMvc.perform(post(RECOMMENDATIONS_URL)
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\":\"help me\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resources").isEmpty())
			.andExpect(jsonPath("$.explanation").value(
				org.hamcrest.Matchers.containsString("too broad")
			));
	}

	@Test
	void pdfRecommendationsIdentifyConceptsAndReturnRelevantResources() throws Exception {
		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file",
					"c-functions.pdf",
					"application/pdf",
					textPdf("C functions let programmers reuse code. User defined functions in C have a return type.")
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.identifiedTopics[0]").value("C programming"))
			.andExpect(jsonPath("$.recommendations[0].source").value("PDF_CONTENT"))
			.andExpect(jsonPath("$.recommendations[0].resources.length()").value(2))
			.andExpect(jsonPath("$.recommendations[0].resources[0].url")
				.value("https://www.learn-c.org/en/Functions"))
			.andExpect(jsonPath("$.maxPdfBytes").value(20 * 1024 * 1024))
			.andExpect(jsonPath("$.maxPdfPages").value(300))
			.andExpect(jsonPath("$.maxExtractedCharacters").value(12000));

		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file",
					"unmatched.pdf",
					"application/pdf",
					textPdf("A single abstract statement with no known computer science or biology topic.")
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.identifiedTopics").isEmpty())
			.andExpect(jsonPath("$.recommendations").isEmpty())
			.andExpect(jsonPath("$.summary").value(
				org.hamcrest.Matchers.containsString("enter a topic manually")
			));
	}

	@Test
	void pdfRecommendationsRejectInvalidEmptyOversizedAndUnreadablePdfs() throws Exception {
		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file", "empty.pdf", "application/pdf", new byte[0]
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Choose a non-empty PDF file."));

		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file", "notes.txt", "text/plain", "not a PDF".getBytes()
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Choose a valid PDF file."));

		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file", "corrupt.pdf", "application/pdf", "%PDF-not readable".getBytes()
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("could not be read")
			));

		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file", "scan.pdf", "application/pdf", textPdf("")
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("Scanned or image-only PDFs are not supported")
			));

		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(new org.springframework.mock.web.MockMultipartFile(
					"file",
					"oversized.pdf",
					"application/pdf",
					new byte[(20 * 1024 * 1024) + 1]
				))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("PDF files must be no larger than 20 MB."));
	}

	@Test
	void pdfRecommendationEndpointRequiresAuthenticationAndCsrf() throws Exception {
		var file = new org.springframework.mock.web.MockMultipartFile(
			"file",
			"notes.pdf",
			"application/pdf",
			textPdf("C functions organize reusable program logic.")
		);
		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf").file(file).with(csrf()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/student/resources/recommendations/pdf")
				.file(file)
				.with(user(studentEmail)))
			.andExpect(status().isForbidden());
	}

	private static byte[] textPdf(String text) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			if (!text.isEmpty()) {
				try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
					content.beginText();
					content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					content.newLineAtOffset(50, 700);
					content.showText(text);
					content.endText();
				}
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private QuizMaterialResponse addMaterial(String email, String topic, String sourceText) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/student/quiz/materials")
				.with(user(email))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"topic":"%s","sourceText":"%s"}
					""".formatted(topic, sourceText)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), QuizMaterialResponse.class);
	}

	private void answerConcept(Long materialId, String answer) throws Exception {
		MvcResult attemptResult = mockMvc.perform(post("/api/student/quiz/materials/" + materialId + "/attempts")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn();
		QuizAttemptResponse attempt = objectMapper.readValue(
			attemptResult.getResponse().getContentAsString(),
			QuizAttemptResponse.class
		);
		String option = answer.equalsIgnoreCase("wrong") ? "B" : "A";
		mockMvc.perform(post("/api/student/quiz/attempts/" + attempt.attemptId() + "/answers")
				.with(user(studentEmail))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"conceptId":%d,"answer":"%s"}
					""".formatted(attempt.questions().getFirst().conceptId(), option)))
			.andExpect(status().isOk());
	}
}
