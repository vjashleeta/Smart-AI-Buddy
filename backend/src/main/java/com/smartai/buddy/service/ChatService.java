package com.smartai.buddy.service;

import com.smartai.buddy.dto.ChatMessageResponse;
import com.smartai.buddy.dto.ChatTurn;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;

@Service
public class ChatService {

	private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";
	private static final String GEMINI_GENERATE_CONTENT_URL =
		"https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";
	private static final String SYSTEM_INSTRUCTION =
		"You are AI Buddy, a helpful study assistant. Give clear, accurate educational explanations. "
			+ "For ordinary questions, state the main answer first, then use about 3-6 short, focused Markdown "
			+ "bullet points for the most useful details. Use simple student-friendly language and avoid one long "
			+ "paragraph when bullets would be clearer. Use a brief heading only when helpful. Do not repeat or "
			+ "nest bullets unnecessarily. When a detailed step-by-step explanation is explicitly requested, "
			+ "provide the requested detail. "
			+ "Preserve equations, code, tables, and other structured formats when "
			+ "they are more suitable; do not force every response into bullets.";
	private static final String SOCRATIC_SYSTEM_INSTRUCTION =
		"You are a patient Socratic tutor. For a new topic, ask one concise guiding question instead of giving "
			+ "the solution. Respond to the student's reasoning with brief feedback and the next useful question. "
			+ "When the student asks for a hint, give the smallest useful hint without revealing the full solution. "
			+ "If the student explicitly asks for an explanation or has made a genuine attempt and is stuck, explain "
			+ "the concept clearly, then check their understanding with one question. Keep explanations accurate, "
			+ "encouraging, and focused on learning.";
	private static final int MAX_PROVIDER_ERROR_LENGTH = 500;
	private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

	private final RestClient restClient;
	private final String apiKey;

	public ChatService(
		RestClient.Builder restClientBuilder,
		@Value("${smart-ai-buddy.ai.api-key:}") String apiKey
	) {
		this.restClient = restClientBuilder.build();
		this.apiKey = apiKey;
	}

	public ChatMessageResponse answer(String question) {
		return answer(question, List.of());
	}

	public ChatMessageResponse answer(String question, List<ChatTurn> history) {
		return answer(question, history, SYSTEM_INSTRUCTION);
	}

	public ChatMessageResponse answerSocratic(String question, List<ChatTurn> history) {
		return answer(question, history, SOCRATIC_SYSTEM_INSTRUCTION);
	}

	private ChatMessageResponse answer(String question, List<ChatTurn> history, String systemInstruction) {
		if (apiKey.isBlank()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Gemini chat is not configured. Set the GEMINI_API_KEY environment variable."
			);
		}

		List<Map<String, Object>> contents = new java.util.ArrayList<>();
		for (ChatTurn turn : history) {
			contents.add(Map.of(
				"role", turn.role(),
				"parts", List.of(Map.of("text", turn.content()))
			));
		}
		contents.add(Map.of(
			"role", "user",
			"parts", List.of(Map.of("text", question.strip()))
		));

		Map<String, Object> request = Map.of(
			"systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
			"contents", contents
		);

		try {
			JsonNode response = restClient.post()
				.uri(GEMINI_GENERATE_CONTENT_URL)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(JsonNode.class);
			StringBuilder answerText = new StringBuilder();
			if (response != null) {
				for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
					answerText.append(part.path("text").asText(""));
				}
			}
			String answer = answerText.toString();
			if (answer.isBlank()) {
				throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Gemini returned no answer. The response may have been blocked by safety settings."
				);
			}
			return new ChatMessageResponse(answer);
		} catch (RestClientResponseException exception) {
			String providerMessage = providerErrorMessage(exception);
			String failureMessage = "Gemini rejected the chat request (HTTP "
				+ exception.getStatusCode().value() + ").";
			if (!providerMessage.isBlank()) {
				failureMessage = "Gemini rejected the chat request (HTTP "
					+ exception.getStatusCode().value() + "): " + providerMessage;
			}
			logger.warn("{}", failureMessage);
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				failureMessage
			);
		} catch (ResourceAccessException exception) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Gemini could not be reached. Check network connectivity and GEMINI_API_KEY."
			);
		} catch (RestClientException exception) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini returned an invalid response."
			);
		}
	}

	private String providerErrorMessage(RestClientResponseException exception) {
		try {
			JsonNode response = exception.getResponseBodyAs(JsonNode.class);
			String message = response == null ? "" : response.path("error").path("message").asText("");
			if (message.isBlank()) {
				return "";
			}
			String sanitizedMessage = message.replace(apiKey, "[redacted]").strip();
			return sanitizedMessage.substring(0, Math.min(sanitizedMessage.length(), MAX_PROVIDER_ERROR_LENGTH));
		} catch (RestClientException exceptionWhileReadingError) {
			return "";
		}
	}
}
