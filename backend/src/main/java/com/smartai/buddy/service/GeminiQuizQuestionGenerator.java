package com.smartai.buddy.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GeminiQuizQuestionGenerator {

	private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";
	private static final String GEMINI_GENERATE_CONTENT_URL =
		"https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";
	private static final String SYSTEM_INSTRUCTION = """
		Create clear, unambiguous educational multiple-choice questions for a student.
		Use the provided source material and target sentence as the primary grounding. Do not state that
		general knowledge comes from the source. Do not copy a list of options from the source material.
		Create three plausible, subject-relevant distractors that test understanding. Use the supplied
		expected answer verbatim as the only correct option. Each question must have exactly four distinct
		options labelled A, B, C, D and exactly one clearly correct answer. Quote the target sentence
		verbatim as sourceEvidence. Keep explanation focused on why the answer fits; do not claim that
		additional facts come from the source. Return only the requested JSON.
		""";
	private static final Logger logger = LoggerFactory.getLogger(GeminiQuizQuestionGenerator.class);
	private static final List<String> OPTION_LABELS = List.of("A", "B", "C", "D");

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;

	public GeminiQuizQuestionGenerator(
		RestClient.Builder restClientBuilder,
		ObjectMapper objectMapper,
		@Value("${smart-ai-buddy.ai.api-key:}") String apiKey
	) {
		this.restClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
	}

	public List<GeneratedQuestion> generate(String topic, String sourceText, List<ConceptInput> concepts) {
		if (apiKey.isBlank()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Quiz question generation is not configured. Set the GEMINI_API_KEY environment variable."
			);
		}
		if (concepts.isEmpty()) {
			throw invalidProviderOutput();
		}

		List<Map<String, Object>> requestedConcepts = concepts.stream()
			.map(concept -> Map.<String, Object>of(
				"conceptId", concept.conceptId(),
				"sourceSentence", concept.sourceSentence(),
				"expectedAnswer", concept.expectedAnswer()
			))
			.toList();
		Map<String, Object> request = Map.of(
			"systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))),
			"contents", List.of(Map.of(
				"role", "user",
				"parts", List.of(Map.of("text", """
					Create exactly one multiple-choice question for each supplied concept, in the same order.
					For each question return conceptId, prompt (a direct question), options (an array of
					exactly four {label,text} objects in A-D order), correctOption (A-D), sourceEvidence
					(the exact target sentence), explanation, and unambiguous (true only when one answer is
					clearly best).
					Return JSON with a single "questions" array. Use concise options; do not make all correct
					answers the same label. Use general knowledge only where helpful and never attribute it
					to the source unless the source supports it.

					Topic: %s
					Source material: %s
					Target concepts: %s
					""".formatted(topic, sourceText, requestedConcepts))
			))),
			"generationConfig", Map.of("responseMimeType", "application/json")
		);

		try {
			JsonNode response = restClient.post()
				.uri(GEMINI_GENERATE_CONTENT_URL)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(JsonNode.class);
			StringBuilder responseText = new StringBuilder();
			if (response != null) {
				for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
					responseText.append(part.path("text").asText(""));
				}
			}
			if (responseText.isEmpty()) {
				throw invalidProviderOutput();
			}
			return validate(responseText.toString(), concepts);
		} catch (RestClientResponseException exception) {
			logger.warn("Gemini rejected quiz question generation (HTTP {}).", exception.getStatusCode().value());
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini could not generate quiz questions (HTTP " + exception.getStatusCode().value() + ")."
			);
		} catch (ResourceAccessException exception) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Gemini could not be reached to generate quiz questions."
			);
		} catch (RestClientException exception) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini returned an invalid quiz question response."
			);
		}
	}

	private List<GeneratedQuestion> validate(String responseText, List<ConceptInput> concepts) {
		try {
			JsonNode questions = objectMapper.readTree(responseText).path("questions");
			if (!questions.isArray() || questions.size() != concepts.size()) {
				throw invalidProviderOutput();
			}
			List<GeneratedQuestion> generated = new ArrayList<>();
			Set<String> normalizedPrompts = new HashSet<>();
			for (int index = 0; index < concepts.size(); index++) {
				ConceptInput concept = concepts.get(index);
				JsonNode question = questions.get(index);
				String prompt = requiredText(question, "prompt", 2000);
				String correctOption = requiredText(question, "correctOption", 1);
				String sourceEvidence = requiredText(question, "sourceEvidence", 2000);
				String explanation = requiredText(question, "explanation", 2000);
				if (!question.path("conceptId").canConvertToLong()
					|| question.path("conceptId").longValue() != concept.conceptId()
					|| !prompt.endsWith("?")
					|| !sourceEvidence.equals(concept.sourceSentence())
					|| !question.path("unambiguous").isBoolean()
					|| !question.path("unambiguous").booleanValue()
					|| !OPTION_LABELS.contains(correctOption)
					|| !normalizedPrompts.add(normalize(prompt))) {
					throw invalidProviderOutput();
				}

				JsonNode optionsNode = question.path("options");
				if (!optionsNode.isArray() || optionsNode.size() != OPTION_LABELS.size()) {
					throw invalidProviderOutput();
				}
				List<GeneratedOption> options = new ArrayList<>();
				Set<String> normalizedOptions = new HashSet<>();
				for (int optionIndex = 0; optionIndex < OPTION_LABELS.size(); optionIndex++) {
					JsonNode optionNode = optionsNode.get(optionIndex);
					String label = requiredText(optionNode, "label", 1);
					String text = requiredText(optionNode, "text", 500);
					if (!label.equals(OPTION_LABELS.get(optionIndex))
						|| !normalizedOptions.add(normalize(text))) {
						throw invalidProviderOutput();
					}
					options.add(new GeneratedOption(label, text));
				}

				String expectedAnswer = normalize(concept.expectedAnswer());
				long expectedAnswerOptions = options.stream()
					.filter(option -> normalize(option.text()).equals(expectedAnswer))
					.count();
				String correctText = options.stream()
					.filter(option -> option.label().equals(correctOption))
					.map(GeneratedOption::text)
					.findFirst()
					.orElseThrow(GeminiQuizQuestionGenerator::invalidProviderOutput);
				if (expectedAnswerOptions != 1 || !normalize(correctText).equals(expectedAnswer)) {
					throw invalidProviderOutput();
				}
				generated.add(new GeneratedQuestion(
					concept.conceptId(),
					prompt,
					options,
					correctOption,
					sourceEvidence,
					explanation
				));
			}
			return List.copyOf(generated);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw invalidProviderOutput();
		}
	}

	private static String requiredText(JsonNode node, String field, int maxLength) {
		JsonNode value = node.path(field);
		if (!value.isString()) {
			throw invalidProviderOutput();
		}
		String text = value.stringValue().strip();
		if (text.isEmpty() || text.length() > maxLength) {
			throw invalidProviderOutput();
		}
		return text;
	}

	private static ResponseStatusException invalidProviderOutput() {
		return new ResponseStatusException(
			HttpStatus.BAD_GATEWAY,
			"Gemini returned incomplete, ambiguous, or invalid quiz questions. Please try again."
		);
	}

	private static String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^\\p{L}\\p{N}]+", " ")
			.strip()
			.replaceAll("\\s+", " ");
	}

	public record ConceptInput(Long conceptId, String sourceSentence, String expectedAnswer) {
	}

	public record GeneratedOption(String label, String text) {
	}

	public record GeneratedQuestion(
		Long conceptId,
		String prompt,
		List<GeneratedOption> options,
		String correctOption,
		String sourceEvidence,
		String explanation
	) {
		public GeneratedQuestion {
			options = List.copyOf(options);
		}
	}
}
