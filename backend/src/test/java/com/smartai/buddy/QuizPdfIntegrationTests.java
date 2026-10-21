package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.QuizConcept;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.QuizConceptRepository;
import com.smartai.buddy.repository.StudyMaterialRepository;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.PdfStudyMaterialExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "smart-ai-buddy.quiz.pdf.max-extracted-characters=100")
@AutoConfigureMockMvc
@Transactional
class QuizPdfIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StudyMaterialRepository studyMaterialRepository;

	@Autowired
	private QuizConceptRepository quizConceptRepository;

	@Autowired
	private PdfStudyMaterialExtractor pdfStudyMaterialExtractor;

	private String studentEmail;

	@BeforeEach
	void setUp() {
		studentEmail = "quiz-pdf-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("PDF Quiz Student", studentEmail, "test-hash"));
	}

	@Test
	void extractsPdfTextAndCreatesQuestionsFromThatContent() throws Exception {
		byte[] pdf = textPdf("Mitochondria produce energy during cellular respiration.");

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "C:\\fakepath\\cell-notes.pdf", "application/pdf", pdf))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.topic").value("Cell biology"))
			.andExpect(jsonPath("$.originalFilename").value("cell-notes.pdf"))
			.andExpect(jsonPath("$.conceptCount").value(1));

		QuizConcept concept = quizConceptRepository
			.findAllByMaterial_Student_IdOrderByMaterial_CreatedAtAscSequenceNumberAsc(studentId())
			.getFirst();
		assertTrue(concept.getSourceSentence().contains("Mitochondria produce energy"));
		assertTrue(concept.getQuestionPrompt().contains("________ produce energy"));
		assertTrue(studyMaterialRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId()).getFirst().getSourceText()
			.contains("Mitochondria produce energy"));
		assertEquals(
			"cell-notes.pdf",
			studyMaterialRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId()).getFirst().getSourceFilename()
		);
	}

	@Test
	void uploadNotesPageUsesTheAuthenticatedPdfUploadAndSavedMaterialEndpoints() throws Exception {
		String uploadHtml = mockMvc.perform(get("/pages/upload.html").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(uploadHtml.contains("data-upload-form"));
		assertTrue(uploadHtml.contains("data-uploaded-notes"));
		assertTrue(uploadHtml.contains("accept=\"application/pdf,.pdf\""));
		assertFalse(uploadHtml.contains("data-demo-form"));
		assertTrue(uploadHtml.contains("the original PDF is not retained"));

		String appJs = mockMvc.perform(get("/js/app.js"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(appJs.contains("formData.append('file', file);"));
		assertTrue(appJs.contains("formData.append('topic', topic);"));
		assertTrue(appJs.contains("'/api/student/quiz/materials/pdf'"));
		assertTrue(appJs.contains("'/api/student/quiz'"));
	}

	@Test
	void pdfFormHasItsOwnTopicAndSubmitsThatValue() throws Exception {
		String quizHtml = mockMvc.perform(get("/pages/quiz.html").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		int pdfFormStart = quizHtml.indexOf("data-quiz-pdf-form");
		int pdfFormEnd = quizHtml.indexOf("</form>", pdfFormStart);
		assertTrue(pdfFormStart >= 0 && pdfFormEnd > pdfFormStart);
		String pdfFormMarkup = quizHtml.substring(pdfFormStart, pdfFormEnd);
		assertTrue(pdfFormMarkup.contains(
			"id=\"quiz-pdf-topic\" name=\"topic\" data-quiz-topic-input maxlength=\"200\""
		));
		assertTrue(pdfFormMarkup.contains("name=\"file\" type=\"file\""));

		String appJs = mockMvc.perform(get("/js/app.js"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(appJs.contains("formData.append('topic', pdfForm.elements.namedItem('topic').value);"));
		assertFalse(appJs.contains("formData.append('topic', materialForm.elements.namedItem('topic').value);"));
	}

	@Test
	void removesUploadedPdfFromActiveMaterialsButRetainsItsConcepts() throws Exception {
		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"cell-notes.pdf",
					"application/pdf",
					textPdf("Mitochondria produce energy during cellular respiration.")
				))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isCreated());
		var material = studyMaterialRepository.findAllByStudent_IdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId())
			.getFirst();

		mockMvc.perform(delete("/api/student/quiz/materials/" + material.getId())
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isNoContent());

		assertTrue(studyMaterialRepository.findById(material.getId()).orElseThrow().isDeleted());
		assertEquals(1, quizConceptRepository.countByMaterial_Id(material.getId()));
		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials").isEmpty())
			.andExpect(jsonPath("$.concepts").isEmpty());
	}

	@Test
	void rejectsEmptyInvalidOversizedAndImageOnlyPdfs() throws Exception {
		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "notes.docx", "application/pdf", textPdf("A valid PDF body.")))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "not-a-pdf.pdf", "application/pdf", "not a PDF".getBytes()))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"too-large.pdf",
					"application/pdf",
					new byte[(20 * 1024 * 1024) + 1]
				))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("PDF files must be no larger than 20 MB."));

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "scan.pdf", "application/pdf", blankPdf()))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("Scanned or image-only PDFs are not supported")
			));

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"long-notes.pdf",
					"application/pdf",
					textPdf("Photosynthesis converts light energy into chemical energy. ".repeat(4))
				))
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("too much text")
			));

		assertTrue(studyMaterialRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId()).isEmpty());
	}

	@Test
	void acceptsPdfByteAndPageBoundariesAndRejectsValuesAboveThem() throws Exception {
		int maxPdfBytes = 20 * 1024 * 1024;
		byte[] sourcePdf = textPdf("Mitochondria produce energy during cellular respiration.");
		byte[] paddedPdf = Arrays.copyOf(sourcePdf, maxPdfBytes);
		Arrays.fill(paddedPdf, sourcePdf.length, paddedPdf.length, (byte) ' ');

		assertTrue(pdfStudyMaterialExtractor.extract("notes.pdf", "application/pdf", paddedPdf)
			.contains("Mitochondria produce energy"));
		ResponseStatusException oversized = assertThrows(
			ResponseStatusException.class,
			() -> pdfStudyMaterialExtractor.extract(
				"notes.pdf",
				"application/pdf",
				Arrays.copyOf(paddedPdf, maxPdfBytes + 1)
			)
		);
		assertTrue(oversized.getReason().contains("20 MB"));

		assertTrue(pdfStudyMaterialExtractor.extract(
			"notes.pdf",
			"application/pdf",
			textPdfWithPages(300, "Mitochondria produce energy during cellular respiration.")
		).contains("Mitochondria produce energy"));
		ResponseStatusException tooManyPages = assertThrows(
			ResponseStatusException.class,
			() -> pdfStudyMaterialExtractor.extract(
				"notes.pdf",
				"application/pdf",
				textPdfWithPages(301, "Mitochondria produce energy during cellular respiration.")
			)
		);
		assertTrue(tooManyPages.getReason().contains("300 pages"));

		assertEquals(100, pdfStudyMaterialExtractor.extract(
			"notes.pdf",
			"application/pdf",
			textPdf("A".repeat(100))
		).length());
		ResponseStatusException tooMuchText = assertThrows(
			ResponseStatusException.class,
			() -> pdfStudyMaterialExtractor.extract("notes.pdf", "application/pdf", textPdf("A".repeat(101)))
		);
		assertTrue(tooMuchText.getReason().contains("100 extracted characters"));
	}

	@Test
	void acceptsTopicAtConfiguredLimitAndRejectsLongerPdfTopic() throws Exception {
		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"cell-notes.pdf",
					"application/pdf",
					textPdf("Mitochondria produce energy during cellular respiration.")
				))
				.param("topic", "")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Enter a topic of no more than 200 characters."));

		String topicAtLimit = "T".repeat(200);
		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"cell-notes.pdf",
					"application/pdf",
					textPdf("Mitochondria produce energy during cellular respiration.")
				))
				.param("topic", topicAtLimit)
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.topic").value(topicAtLimit));

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile(
					"file",
					"cell-notes.pdf",
					"application/pdf",
					textPdf("Ribosomes synthesize proteins in cells.")
				))
				.param("topic", "T".repeat(201))
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Enter a topic of no more than 200 characters."));
	}

	@Test
	void requiresAuthenticationAndCsrfAndKeepsPdfMaterialStudentScoped() throws Exception {
		String otherEmail = "quiz-pdf-other-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Other PDF Student", otherEmail, "test-hash"));
		byte[] pdf = textPdf("Mitochondria produce energy during cellular respiration.");
		MockMultipartFile material = new MockMultipartFile("file", "cell-notes.pdf", "application/pdf", pdf);

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(material)
				.param("topic", "Cell biology")
				.with(csrf()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(material)
				.param("topic", "Cell biology")
				.with(user(studentEmail)))
			.andExpect(status().isForbidden());

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(material)
				.param("topic", "Cell biology")
				.with(user(studentEmail))
				.with(csrf()))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/student/quiz").with(user(otherEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials").isEmpty())
			.andExpect(jsonPath("$.concepts").isEmpty());

		mockMvc.perform(get("/api/student/quiz").with(user(studentEmail)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials[0].originalFilename").value("cell-notes.pdf"))
			.andExpect(jsonPath("$.maxTopicCharacters").value(200))
			.andExpect(jsonPath("$.maxPdfBytes").value(20 * 1024 * 1024))
			.andExpect(jsonPath("$.maxPdfPages").value(300))
			.andExpect(jsonPath("$.maxExtractedCharacters").value(100));
	}

	@Test
	void allowsCrossOriginPreflightForRemovingSavedNotes() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.options("/api/student/quiz/materials/1")
				.header("Origin", "http://localhost:5500")
				.header("Access-Control-Request-Method", "DELETE")
				.header("Access-Control-Request-Headers", "x-csrf-token"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("DELETE")));
	}

	private Long studentId() {
		return userRepository.findByCollegeEmail(studentEmail).orElseThrow().getId();
	}

	private static byte[] textPdf(String text) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(50, 700);
				content.showText(text);
				content.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private static byte[] textPdfWithPages(int pageCount, String firstPageText) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			for (int page = 0; page < pageCount; page++) {
				document.addPage(new PDPage());
			}
			try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(50, 700);
				content.showText(firstPageText);
				content.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private static byte[] blankPdf() throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			return output.toByteArray();
		}
	}
}
