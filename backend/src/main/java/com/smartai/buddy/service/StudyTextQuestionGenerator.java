package com.smartai.buddy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StudyTextQuestionGenerator {

	private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+|[\\r\\n]+");
	private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}\\p{N}'’-]*");
	private static final Set<String> NON_CONCEPT_WORDS = Set.of(
		"a", "an", "and", "are", "as", "at", "be", "because", "been", "being", "by",
		"can", "for", "from", "has", "have", "in", "into", "is", "it", "its", "of",
		"on", "or", "that", "the", "their", "then", "these", "this", "those", "to",
		"was", "were", "which", "with"
	);

	public List<GeneratedQuestion> generate(String topic, String sourceText) {
		List<GeneratedQuestion> questions = new ArrayList<>();
		for (String sentencePart : SENTENCE_SEPARATOR.split(sourceText.strip())) {
			String sentence = sentencePart.strip();
			if (sentence.length() < 20 || sentence.length() > 1750) {
				continue;
			}

			Matcher matcher = WORD.matcher(sentence);
			while (matcher.find()) {
				String answer = matcher.group();
				String normalized = answer.toLowerCase(Locale.ROOT);
				if (answer.length() < 4 || answer.length() > 120 || NON_CONCEPT_WORDS.contains(normalized)) {
					continue;
				}

				String clozeSentence = sentence.substring(0, matcher.start())
					+ "________"
					+ sentence.substring(matcher.end());
				questions.add(new GeneratedQuestion(
					questions.size() + 1,
					sentence,
					"Complete this statement from your " + topic + " material: " + clozeSentence,
					answer
				));
				break;
			}
		}
		return List.copyOf(questions);
	}

	public record GeneratedQuestion(int sequenceNumber, String sourceSentence, String prompt, String answer) {
	}
}
