package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.smartai.buddy.dto.ChatTurn;
import com.smartai.buddy.service.ChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class ChatServiceTests {

	@Test
	void postsQuestionToConfiguredProviderAndReturnsProviderResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(
			builder,
			"test-api-key"
		);
		String answer = "A conditional probability includes the condition as its reference group.";
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-api-key"))
			.andExpect(header("Content-Type", "application/json"))
			.andExpect(jsonPath("$.systemInstruction.parts[0].text").value(
				org.hamcrest.Matchers.allOf(
					org.hamcrest.Matchers.containsString("3-6 short, focused Markdown bullet points"),
					org.hamcrest.Matchers.containsString("detailed step-by-step explanation is explicitly requested"),
					org.hamcrest.Matchers.containsString("Preserve equations, code, tables")
				)
			))
			.andExpect(jsonPath("$.contents[0].role").value("user"))
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("Explain conditional probability"))
			.andRespond(withSuccess("""
				{"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
				""".formatted(answer), MediaType.APPLICATION_JSON));

		assertEquals(answer, service.answer(" Explain conditional probability ").answer());
		server.verify();
	}

	@Test
	void reportsGeminiErrorDetailsWithoutExposingTheApiKey() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(builder, "test-api-key");
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andRespond(withStatus(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{"error":{"message":"Unknown model for key test-api-key","status":"NOT_FOUND"}}
					"""));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.answer("Explain recursion")
		);

		assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
		assertEquals(
			"Gemini rejected the chat request (HTTP 404): Unknown model for key [redacted]",
			exception.getReason()
		);
		server.verify();
	}

	@Test
	void reportsExactMissingProviderConfigurationInsteadOfFabricatingAnAnswer() {
		ChatService service = new ChatService(RestClient.builder(), "");

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.answer("Explain recursion")
		);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
		assertEquals(
			"Gemini chat is not configured. Set the GEMINI_API_KEY environment variable.",
			exception.getReason()
		);
	}

	@Test
	void doesNotFallBackToAnotherModelWhenGeminiReturnsMultipleTextParts() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(builder, "test-api-key");
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("""
				{"candidates":[{"content":{"parts":[{"text":"First part. "},{"text":"Second part."}]}}]}
				""", MediaType.APPLICATION_JSON));

		assertEquals("First part. Second part.", service.answer("Explain recursion").answer());
		server.verify();
	}

	@Test
	void preservesConciseBulletedGeminiAnswerForTheFrontend() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(builder, "test-api-key");
		String answer = "Photosynthesis converts light energy into chemical energy.\n"
			+ "- Chlorophyll absorbs light.\n"
			+ "- The plant uses carbon dioxide and water.\n"
			+ "- Glucose and oxygen are produced.";
		String escapedAnswer = answer.replace("\n", "\\n");
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andExpect(jsonPath("$.systemInstruction.parts[0].text").value(
				org.hamcrest.Matchers.containsString("state the main answer first")
			))
			.andRespond(withSuccess("""
				{"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
				""".formatted(escapedAnswer), MediaType.APPLICATION_JSON));

		assertEquals(answer, service.answer("Explain photosynthesis briefly").answer());
		server.verify();
	}

	@Test
	void sendsRecentConversationTurnsBeforeTheCurrentQuestionUsingGeminiRoles() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(builder, "test-api-key");
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andExpect(jsonPath("$.contents.length()").value(3))
			.andExpect(jsonPath("$.contents[0].role").value("user"))
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("What is a variable?"))
			.andExpect(jsonPath("$.contents[1].role").value("model"))
			.andExpect(jsonPath("$.contents[1].parts[0].text").value("A named value."))
			.andExpect(jsonPath("$.contents[2].role").value("user"))
			.andExpect(jsonPath("$.contents[2].parts[0].text").value("Can you give an example?"))
			.andRespond(withSuccess("""
				{"candidates":[{"content":{"parts":[{"text":"For example, x = 3."}]}}]}
				""", MediaType.APPLICATION_JSON));

		assertEquals(
			"For example, x = 3.",
			service.answer("Can you give an example?", List.of(
				new ChatTurn("user", "What is a variable?"),
				new ChatTurn("model", "A named value.")
			)).answer()
		);
		server.verify();
	}

	@Test
	void usesDedicatedSocraticInstructionAndIncludesFollowUpHistory() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ChatService service = new ChatService(builder, "test-api-key");
		server.expect(requestTo(
				"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
			))
			.andExpect(jsonPath("$.systemInstruction.parts[0].text").value(
				org.hamcrest.Matchers.containsString("patient Socratic tutor")
			))
			.andExpect(jsonPath("$.contents.length()").value(3))
			.andExpect(jsonPath("$.contents[0].role").value("user"))
			.andExpect(jsonPath("$.contents[1].role").value("model"))
			.andExpect(jsonPath("$.contents[2].parts[0].text").value("Can I have a hint?"))
			.andRespond(withSuccess("""
				{"candidates":[{"content":{"parts":[{"text":"What smaller step can you try first?"}]}}]}
				""", MediaType.APPLICATION_JSON));

		assertEquals(
			"What smaller step can you try first?",
			service.answerSocratic("Can I have a hint?", List.of(
				new ChatTurn("user", "I am learning derivatives."),
				new ChatTurn("model", "What do you know about the power rule?")
			)).answer()
		);
		server.verify();
	}
}
