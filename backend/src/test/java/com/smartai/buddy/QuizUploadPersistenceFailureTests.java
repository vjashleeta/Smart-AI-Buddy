package com.smartai.buddy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartai.buddy.entity.StudyMaterial;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.StudyMaterialRepository;
import com.smartai.buddy.repository.UserRepository;
import com.smartai.buddy.service.PdfStudyMaterialExtractor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuizUploadPersistenceFailureTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private StudyMaterialRepository studyMaterialRepository;

	@MockitoBean
	private PdfStudyMaterialExtractor pdfStudyMaterialExtractor;

	@Test
	void persistenceFailureReturnsSafeErrorInsteadOfUploadSuccess() throws Exception {
		String email = "upload-failure-" + UUID.randomUUID() + "@college.edu";
		userRepository.saveAndFlush(new User("Upload Failure Student", email, "test-hash"));
		when(pdfStudyMaterialExtractor.extract(anyString(), anyString(), any(byte[].class)))
			.thenReturn("Mitochondria produce energy during cellular respiration.");
		when(studyMaterialRepository.saveAndFlush(any(StudyMaterial.class)))
			.thenThrow(new DataAccessResourceFailureException("C:\\private\\storage\\internal"));

		mockMvc.perform(multipart("/api/student/quiz/materials/pdf")
				.file(new MockMultipartFile("file", "cell-notes.pdf", "application/pdf", new byte[] {1}))
				.param("topic", "Cell biology")
				.with(user(email))
				.with(csrf()))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.message")
				.value("The study request could not be completed. Please try again."))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("private")
			)));

		verify(studyMaterialRepository).saveAndFlush(any(StudyMaterial.class));
	}
}
