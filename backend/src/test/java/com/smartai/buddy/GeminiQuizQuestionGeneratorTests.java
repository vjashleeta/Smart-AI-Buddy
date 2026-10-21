package com.smartai.buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartai.buddy.service.GeminiQuizQuestionGenerator;
import com.smartai.buddy.service.GeminiQuizQuestionGenerator.ConceptInput;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class GeminiQuizQuestionGeneratorTests {

	private static final String GENERATE_CONTENT_URL =
		"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent";
	private static final ConceptInput CONCEPT = new ConceptInput(
		17L,
		"Mitochondria produce energy during cellular respiration.",
		"Mitochondria"
	);

	@Test
	void parsesQuestionWithFourDistinctOptionsAndAnchoredCorrectAnswer() throws Exception {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiQuizQuestionGenerator generator = generator(builder, "test-api-key");
		server.expect(requestTo(GENERATE_CONTENT_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-api-key"))
			.andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
			.andExpect(jsonPath("$.contents[0].parts[0].text")
				.value(org.hamcrest.Matchers.containsString("Mitochondria produce energy")))
			.andRespond(withSuccess(providerResponse(questionJson(
				17,
				"Which organelle is most directly associated with cellular respiration?",
				"\"options\":[{\"label\":\"A\",\"text\":\"Chloroplast\"},"
					+ "{\"label\":\"B\",\"text\":\"Mitochondria\"},"
					+ "{\"label\":\"C\",\"text\":\"Ribosome\"},"
					+ "{\"label\":\"D\",\"text\":\"Golgi apparatus\"}]",
				"B",
				true
			)), MediaType.APPLICATION_JSON));

		List<GeminiQuizQuestionGenerator.GeneratedQuestion> result = generator.generate(
			"Cell biology",
			CONCEPT.sourceSentence(),
			List.of(CONCEPT)
		);

		assertEquals(1, result.size());
		assertEquals("B", result.getFirst().correctOption());
		assertEquals("Mitochondria", result.getFirst().options().get(1).text());
		server.verify();
	}

	@Test
	void rejectsMalformedOutputMissingOptionsDuplicateChoicesAmbiguityAndInvalidAnswerKey() throws Exception {
		assertInvalidResponse("not JSON");
		assertInvalidResponse("""
			{"questions":[{"conceptId":17,"prompt":"Which organelle is relevant?","options":[],
			"correctOption":"A","explanation":"Explanation.","unambiguous":true}]}
			""");
		assertInvalidResponse(questionJson(
			17,
			"Which organelle is relevant?",
			"\"options\":[{\"label\":\"A\",\"text\":\"Mitochondria\"},"
				+ "{\"label\":\"B\",\"text\":\"Mitochondria\"},"
				+ "{\"label\":\"C\",\"text\":\"Ribosome\"},"
				+ "{\"label\":\"D\",\"text\":\"Chloroplast\"}]",
			"A",
			true
		));
		assertInvalidResponse(questionJson(
			17,
			"Which organelle is relevant?",
			validOptions(),
			"A",
			false
		));
		assertInvalidResponse(questionJson(
			17,
			"Which organelle is relevant?",
			validOptions(),
			"B",
			true
		));
		assertInvalidResponse(questionJson(
			18,
			"Which organelle is relevant?",
			validOptions(),
			"A",
			true
		));
		assertInvalidResponse(questionJson(
			17,
			"Which organelle is relevant?",
			validOptions(),
			"A",
			true
		).replace(
			"Mitochondria produce energy during cellular respiration.",
			"Chloroplasts convert light into chemical energy."
		));
	}

	@Test
	void returnsExplicitConfigurationAndProviderFailures() {
		ResponseStatusException unconfigured = assertThrows(
			ResponseStatusException.class,
			() -> generator(RestClient.builder(), "").generate(
				"Cell biology",
				CONCEPT.sourceSentence(),
				List.of(CONCEPT)
			)
		);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unconfigured.getStatusCode());

		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(GENERATE_CONTENT_URL))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"message\":\"provider unavailable\"}}"));
		ResponseStatusException providerFailure = assertThrows(
			ResponseStatusException.class,
			() -> generator(builder, "test-api-key").generate(
				"Cell biology",
				CONCEPT.sourceSentence(),
				List.of(CONCEPT)
			)
		);
		assertEquals(HttpStatus.BAD_GATEWAY, providerFailure.getStatusCode());
		server.verify();
	}

	private static void assertInvalidResponse(String json) throws Exception {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(GENERATE_CONTENT_URL))
			.andRespond(withSuccess(providerResponse(json), MediaType.APPLICATION_JSON));
		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> generator(builder, "test-api-key").generate(
				"Cell biology",
				CONCEPT.sourceSentence(),
				List.of(CONCEPT)
			)
		);
		assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
		server.verify();
	}

	private static GeminiQuizQuestionGenerator generator(RestClient.Builder builder, String apiKey) {
		return new GeminiQuizQuestionGenerator(builder, new ObjectMapper(), apiKey);
	}

	private static String providerResponse(String questionJson) throws Exception {
		String encodedQuestion = new ObjectMapper().writeValueAsString(questionJson);
		return """
			{"candidates":[{"content":{"parts":[{"text":%s}]}}]}
			""".formatted(encodedQuestion);
	}

	private static String questionJson(
		long conceptId,
		String prompt,
		String options,
		String correctOption,
		boolean unambiguous
	) {
		return """
			{"questions":[{"conceptId":%d,"prompt":"%s",%s,"correctOption":"%s",
			"sourceEvidence":"Mitochondria produce energy during cellular respiration.",
			"explanation":"Mitochondria are the answer described by the source.",
			"unambiguous":%s}]}
			""".formatted(conceptId, prompt, options, correctOption, unambiguous);
	}

	private static String validOptions() {
		return "\"options\":[{\"label\":\"A\",\"text\":\"Mitochondria\"},"
			+ "{\"label\":\"B\",\"text\":\"Chloroplast\"},"
			+ "{\"label\":\"C\",\"text\":\"Ribosome\"},"
			+ "{\"label\":\"D\",\"text\":\"Golgi apparatus\"}]";
	}
}
