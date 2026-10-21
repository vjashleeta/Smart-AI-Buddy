package com.smartai.buddy.service;

import com.smartai.buddy.dto.DifficultTopicRequest;
import com.smartai.buddy.dto.LearningResourceResponse;
import com.smartai.buddy.dto.PdfTopicRecommendationResponse;
import com.smartai.buddy.dto.QuizConceptProgressResponse;
import com.smartai.buddy.dto.TopicRecommendationResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ResourceRecommendationService {

	private static final int MINIMUM_QUIZ_ANSWERS = 2;
	private static final int MAX_PDF_TOPICS = 5;
	private static final Set<String> VAGUE_TOPIC_WORDS = Set.of(
		"about", "and", "class", "concept", "help", "learn", "me", "more", "need", "please",
		"study", "teach", "the", "thing", "topic", "understand", "what"
	);
	private static final List<ResourceGroup> CATALOGUE = List.of(
		new ResourceGroup(
			"Cellular energy",
			List.of("cell biology", "cellular respiration", "mitochondria", "atp", "glycolysis"),
			List.of(
				resource("OpenStax Biology 2e: Energy in Living Systems", "LEARNING_PAGE",
					"https://openstax.org/books/biology-2e/pages/7-1-energy-in-living-systems"),
				resource("OpenStax Biology 2e: Glycolysis", "NOTES",
					"https://openstax.org/books/biology-2e/pages/7-2-glycolysis")
			)
		),
		new ResourceGroup(
			"Photosynthesis",
			List.of("photosynthesis", "chloroplast", "light dependent reactions"),
			List.of(resource("OpenStax Biology 2e: Light-Dependent Reactions", "LEARNING_PAGE",
				"https://openstax.org/books/biology-2e/pages/8-2-the-light-dependent-reactions-of-photosynthesis"))
		),
		new ResourceGroup(
			"Statistics and probability",
			List.of("statistics", "probability", "data analysis"),
			List.of(
				resource("OpenStax Introductory Statistics: Probability", "NOTES",
					"https://openstax.org/books/introductory-statistics/pages/3-introduction"),
				resource("OpenStax Introductory Statistics: Introduction", "LEARNING_PAGE",
					"https://openstax.org/books/introductory-statistics/pages/1-introduction"),
				videoSearch("probability and statistics")
			)
		),
		new ResourceGroup(
			"C programming",
			List.of(
				"c programming", "programming in c", "c language", "c functions", "functions in c",
				"function in c", "user defined functions in c", "user defined function in c",
				"c programming functions", "functions c", "c pointers", "pointers in c", "arrays in c"
			),
			List.of(
				resource("Learn-C.org: Functions", "TUTORIAL", "https://www.learn-c.org/en/Functions"),
				resource("GeeksforGeeks: C Functions", "NOTES", "https://www.geeksforgeeks.org/c/c-functions/")
			)
		),
		new ResourceGroup(
			"Java programming",
			List.of(
				"java", "java programming", "programming in java", "java language", "java methods",
				"java classes", "classes and objects in java", "oop in java", "object oriented programming java"
			),
			List.of(
				resource("Dev.java: Learn Java", "LEARNING_PAGE", "https://dev.java/learn/"),
				resource("Dev.java: Classes and Objects", "TUTORIAL",
					"https://dev.java/learn/language/oop/classes-objects/")
			)
		),
		new ResourceGroup(
			"Database management",
			List.of(
				"dbms", "database management", "database management system", "relational database",
				"database normalization", "sql", "database transactions"
			),
			List.of(resource("GeeksforGeeks: DBMS Tutorial", "LEARNING_PAGE",
				"https://www.geeksforgeeks.org/dbms/dbms/"))
		),
		new ResourceGroup(
			"MySQL",
			List.of("mysql", "mysql queries", "mysql database", "mysql joins", "mysql tutorial"),
			List.of(resourceSearch("MySQL documentation", "EDUCATIONAL_SEARCH",
				"site:dev.mysql.com/doc MySQL tutorial"))
		),
		new ResourceGroup(
			"Data structures",
			List.of("data structures", "stack", "queue", "linked list", "array", "deque"),
			List.of(resource("VisuAlgo: Lists, Stacks, Queues, and Deques", "LEARNING_PAGE",
				"https://visualgo.net/en/list"))
		),
		new ResourceGroup(
			"Programming fundamentals",
			List.of(
				"programming fundamentals", "programming basics", "variables and loops",
				"control flow", "algorithms"
			),
			List.of(resourceSearch("Programming fundamentals", "EDUCATIONAL_SEARCH",
				"programming fundamentals tutorial site:edu OR site:learn-c.org OR site:dev.java"))
		),
		new ResourceGroup(
			"Machine learning",
			List.of("machine learning", "supervised learning", "model training"),
			List.of(resource("Google Machine Learning Crash Course", "LEARNING_PAGE",
				"https://developers.google.com/machine-learning/crash-course"))
		)
	);

	private final QuizService quizService;
	private final PdfStudyMaterialExtractor pdfExtractor;

	public ResourceRecommendationService(QuizService quizService, PdfStudyMaterialExtractor pdfExtractor) {
		this.quizService = quizService;
		this.pdfExtractor = pdfExtractor;
	}

	public List<TopicRecommendationResponse> getRecommendations(String studentEmail) {
		List<QuizConceptProgressResponse> progress = quizService.getOverview(studentEmail).concepts();
		Map<String, WeakTopic> weakTopics = new LinkedHashMap<>();
		for (QuizConceptProgressResponse concept : progress) {
			if (concept.totalAttempts() < MINIMUM_QUIZ_ANSWERS
				|| concept.consecutiveCorrect() >= concept.masteryTarget()) {
				continue;
			}
			String key = normalize(concept.topic());
			WeakTopic weakTopic = weakTopics.computeIfAbsent(key, ignored -> new WeakTopic(concept.topic()));
			weakTopic.addEvidence();
		}
		return weakTopics.values().stream()
			.map(topic -> buildRecommendation(topic.topic, "QUIZ_PROGRESS", topic.explanation()))
			.toList();
	}

	public TopicRecommendationResponse recommendForReportedTopic(DifficultTopicRequest request) {
		String topic = request.topic().strip();
		return buildRecommendation(topic, "STUDENT_REPORTED",
			"Recommended because you said this topic feels difficult.");
	}

	public PdfTopicRecommendationResponse recommendFromPdf(String filename, String contentType, byte[] pdfBytes) {
		String extractedText = pdfExtractor.extract(filename, contentType, pdfBytes);
		List<ResourceGroup> identifiedGroups = CATALOGUE.stream()
			.filter(group -> group.matchesText(extractedText))
			.limit(MAX_PDF_TOPICS)
			.toList();
		if (identifiedGroups.isEmpty()) {
			return new PdfTopicRecommendationResponse(
				"No familiar topic keywords were identified in the extracted text. Upload a clearer PDF or enter a topic manually.",
				List.of(),
				List.of(),
				pdfExtractor.getMaxPdfBytes(),
				pdfExtractor.getMaxPdfPages(),
				pdfExtractor.getMaxExtractedCharacters()
			);
		}

		List<String> topics = identifiedGroups.stream().map(ResourceGroup::name).toList();
		List<TopicRecommendationResponse> recommendations = identifiedGroups.stream()
			.map(group -> new TopicRecommendationResponse(
				group.name(),
				"PDF_CONTENT",
				"Matched terms in the PDF's extracted text. This is keyword-based matching, not AI analysis or OCR.",
				group.resources().stream()
					.map(resource -> new LearningResourceResponse(
						resource.title(),
						resource.type(),
						group.name(),
						resource.url()
					))
					.toList()
			))
			.toList();
		return new PdfTopicRecommendationResponse(
			"Identified likely topics by matching known educational terms in the PDF text.",
			topics,
			recommendations,
			pdfExtractor.getMaxPdfBytes(),
			pdfExtractor.getMaxPdfPages(),
			pdfExtractor.getMaxExtractedCharacters()
		);
	}

	private static TopicRecommendationResponse buildRecommendation(String topic, String source, String explanation) {
		List<LearningResourceResponse> matches = matchedGroups(topic).stream()
			.flatMap(group -> group.resources().stream())
			.map(resource -> new LearningResourceResponse(resource.title(), resource.type(), topic, resource.url()))
			.distinct()
			.toList();
		if (matches.isEmpty() && isSpecificTopic(topic)) {
			matches = List.of(
				resourceSearch("Educational search results for " + topic, "EDUCATIONAL_SEARCH",
					topic + " tutorial notes site:edu OR site:openstax.org OR site:learn-c.org OR site:dev.java"),
				videoSearch(topic)
			);
		}
		String message = matches.isEmpty()
			? explanation + " This topic is too broad for a useful safe search. Try adding a subject or a specific concept."
			: explanation;
		return new TopicRecommendationResponse(topic, source, message, matches);
	}

	private static List<ResourceGroup> matchedGroups(String topic) {
		return CATALOGUE.stream().filter(group -> group.matchesTopic(topic)).toList();
	}

	private static boolean isSpecificTopic(String topic) {
		long meaningfulWords = words(topic).stream()
			.filter(word -> word.length() > 1)
			.filter(word -> !VAGUE_TOPIC_WORDS.contains(word))
			.count();
		return meaningfulWords >= 2;
	}

	private static LearningResourceResponse resource(String title, String type, String url) {
		return new LearningResourceResponse(title, type, "", url);
	}

	private static LearningResourceResponse resourceSearch(String title, String type, String query) {
		return resource(title, type,
			"https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
	}

	private static LearningResourceResponse videoSearch(String topic) {
		String query = "Khan Academy " + topic + " tutorial";
		return resource("Khan Academy video search: " + topic, "VIDEO_SEARCH",
			"https://www.youtube.com/results?search_query="
				+ URLEncoder.encode(query, StandardCharsets.UTF_8));
	}

	private static String normalize(String text) {
		return String.join(" ", words(text));
	}

	private static List<String> words(String text) {
		return List.of(text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ")
			.strip().split("\\s+"));
	}

	private record ResourceGroup(String name, List<String> aliases, List<LearningResourceResponse> resources) {
		private boolean matchesTopic(String topic) {
			return aliases.stream().anyMatch(alias -> aliasMatches(topic, alias));
		}

		private boolean matchesText(String text) {
			return aliases.stream().anyMatch(alias -> aliasMatches(text, alias));
		}

	}

	private static boolean aliasMatches(String text, String alias) {
		List<String> textWords = words(text);
		List<String> aliasWords = words(alias);
		if (aliasWords.size() == 1) {
			return textWords.contains(aliasWords.getFirst());
		}
		if (containsPhrase(textWords, aliasWords)) {
			return true;
		}
		Set<String> distinctAliasWords = new LinkedHashSet<>(aliasWords);
		return distinctAliasWords.stream().allMatch(textWords::contains);
	}

	private static boolean containsPhrase(List<String> text, List<String> phrase) {
		if (phrase.size() > text.size()) {
			return false;
		}
		for (int start = 0; start <= text.size() - phrase.size(); start++) {
			if (text.subList(start, start + phrase.size()).equals(phrase)) {
				return true;
			}
		}
		return false;
	}

	private static final class WeakTopic {
		private final String topic;
		private int conceptsNeedingPractice;

		private WeakTopic(String topic) {
			this.topic = topic;
		}

		private void addEvidence() {
			conceptsNeedingPractice++;
		}

		private String explanation() {
			return "Some concepts in this topic are still below the mastery target after at least "
				+ MINIMUM_QUIZ_ANSWERS + " quiz answers; review the " + conceptsNeedingPractice
				+ " concept" + (conceptsNeedingPractice == 1 ? "" : "s") + " that need more practice.";
		}
	}
}
