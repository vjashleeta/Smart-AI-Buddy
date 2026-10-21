package com.smartai.buddy.service;

import com.smartai.buddy.dto.AddQuizMaterialRequest;
import com.smartai.buddy.dto.QuizAnswerResponse;
import com.smartai.buddy.dto.QuizAttemptResponse;
import com.smartai.buddy.dto.QuizConceptProgressResponse;
import com.smartai.buddy.dto.QuizMaterialResponse;
import com.smartai.buddy.dto.QuizOverviewResponse;
import com.smartai.buddy.dto.QuizOptionResponse;
import com.smartai.buddy.dto.QuizQuestionResponse;
import com.smartai.buddy.entity.QuizAnswer;
import com.smartai.buddy.entity.QuizAttempt;
import com.smartai.buddy.entity.QuizAttemptQuestion;
import com.smartai.buddy.entity.QuizConcept;
import com.smartai.buddy.entity.StudyMaterial;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.QuizAnswerRepository;
import com.smartai.buddy.repository.QuizAttemptQuestionRepository;
import com.smartai.buddy.repository.QuizAttemptRepository;
import com.smartai.buddy.repository.QuizConceptRepository;
import com.smartai.buddy.repository.StudyMaterialRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizService {

	private final UserRepository userRepository;
	private final StudyMaterialRepository studyMaterialRepository;
	private final QuizConceptRepository quizConceptRepository;
	private final QuizAttemptRepository quizAttemptRepository;
	private final QuizAttemptQuestionRepository quizAttemptQuestionRepository;
	private final QuizAnswerRepository quizAnswerRepository;
	private final StudyTextQuestionGenerator questionGenerator;
	private final GeminiQuizQuestionGenerator geminiQuizQuestionGenerator;
	private final PdfStudyMaterialExtractor pdfStudyMaterialExtractor;
	private final int masteryTarget;
	private final int questionCount;
	private final int maxTopicCharacters;

	public QuizService(
		UserRepository userRepository,
		StudyMaterialRepository studyMaterialRepository,
		QuizConceptRepository quizConceptRepository,
		QuizAttemptRepository quizAttemptRepository,
		QuizAttemptQuestionRepository quizAttemptQuestionRepository,
		QuizAnswerRepository quizAnswerRepository,
		StudyTextQuestionGenerator questionGenerator,
		GeminiQuizQuestionGenerator geminiQuizQuestionGenerator,
		PdfStudyMaterialExtractor pdfStudyMaterialExtractor,
		@Value("${smart-ai-buddy.quiz.mastery-consecutive-correct:2}") int masteryTarget,
		@Value("${smart-ai-buddy.quiz.questions-per-attempt:5}") int questionCount,
		@Value("${smart-ai-buddy.quiz.topic.max-characters:200}") int maxTopicCharacters
	) {
		if (masteryTarget < 1 || questionCount < 1 || maxTopicCharacters < 1 || maxTopicCharacters > 200) {
			throw new IllegalArgumentException("Quiz settings must be positive and topic length cannot exceed 200.");
		}
		this.userRepository = userRepository;
		this.studyMaterialRepository = studyMaterialRepository;
		this.quizConceptRepository = quizConceptRepository;
		this.quizAttemptRepository = quizAttemptRepository;
		this.quizAttemptQuestionRepository = quizAttemptQuestionRepository;
		this.quizAnswerRepository = quizAnswerRepository;
		this.questionGenerator = questionGenerator;
		this.geminiQuizQuestionGenerator = geminiQuizQuestionGenerator;
		this.pdfStudyMaterialExtractor = pdfStudyMaterialExtractor;
		this.masteryTarget = masteryTarget;
		this.questionCount = questionCount;
		this.maxTopicCharacters = maxTopicCharacters;
	}

	@Transactional(readOnly = true)
	public QuizOverviewResponse getOverview(String studentEmail) {
		User student = findStudent(studentEmail);
		List<QuizConcept> concepts = quizConceptRepository
			.findAllActiveByStudentId(student.getId());
		List<QuizMaterialResponse> materials = studyMaterialRepository
			.findAllByStudent_IdAndDeletedAtIsNullOrderByCreatedAtDesc(student.getId())
			.stream()
			.map(material -> new QuizMaterialResponse(
				material.getId(),
				material.getTopic(),
				material.getSourceFilename(),
				(int) quizConceptRepository.countByMaterial_Id(material.getId()),
				material.getCreatedAt()
			))
			.toList();
		List<QuizConceptProgressResponse> progress = concepts.stream()
			.map(concept -> toProgress(concept, masteryTarget))
			.toList();
		return new QuizOverviewResponse(
			materials,
			progress,
			countStatus(progress, "NOT_YET_TESTED"),
			countStatus(progress, "IN_PROGRESS"),
			countStatus(progress, "NEEDS_REVISION"),
			countStatus(progress, "MASTERED"),
			masteryTarget,
			maxTopicCharacters,
			pdfStudyMaterialExtractor.getMaxPdfBytes(),
			pdfStudyMaterialExtractor.getMaxPdfPages(),
			pdfStudyMaterialExtractor.getMaxExtractedCharacters()
		);
	}

	@Transactional
	public void deleteMaterial(String studentEmail, Long materialId) {
		User student = findStudent(studentEmail);
		StudyMaterial material = studyMaterialRepository
			.findByIdAndStudent_IdAndDeletedAtIsNull(materialId, student.getId())
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"Study material was not found."
			));
		material.markDeleted(LocalDateTime.now());
		studyMaterialRepository.saveAndFlush(material);
	}

	@Transactional
	public QuizMaterialResponse addMaterial(String studentEmail, AddQuizMaterialRequest request) {
		User student = findStudent(studentEmail);
		return saveMaterial(student, request.topic(), request.sourceText());
	}

	@Transactional
	public QuizMaterialResponse addPdfMaterial(
		String studentEmail,
		String topic,
		String filename,
		String contentType,
		byte[] pdfBytes
	) {
		User student = findStudent(studentEmail);
		validateTopic(topic);
		String extractedText = pdfStudyMaterialExtractor.extract(filename, contentType, pdfBytes);
		return saveMaterial(student, topic, extractedText, safeOriginalFilename(filename));
	}

	private QuizMaterialResponse saveMaterial(User student, String suppliedTopic, String suppliedText) {
		return saveMaterial(student, suppliedTopic, suppliedText, null);
	}

	private QuizMaterialResponse saveMaterial(
		User student,
		String suppliedTopic,
		String suppliedText,
		String originalFilename
	) {
		validateTopic(suppliedTopic);
		String topic = suppliedTopic.strip();
		String sourceText = suppliedText.strip();
		List<StudyTextQuestionGenerator.GeneratedQuestion> generated =
			questionGenerator.generate(topic, sourceText);
		if (generated.isEmpty()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Add at least one complete sentence with an identifiable concept before saving study material."
			);
		}

		StudyMaterial material = studyMaterialRepository.saveAndFlush(
			new StudyMaterial(student, topic, sourceText, originalFilename)
		);
		List<QuizConcept> concepts = generated.stream()
			.map(question -> new QuizConcept(
				material,
				question.sequenceNumber(),
				question.sourceSentence(),
				question.prompt(),
				question.answer()
			))
			.toList();
		quizConceptRepository.saveAllAndFlush(concepts);
		return new QuizMaterialResponse(
			material.getId(),
			material.getTopic(),
			material.getSourceFilename(),
			concepts.size(),
			material.getCreatedAt()
		);
	}

	private static String safeOriginalFilename(String filename) {
		if (filename == null || filename.isBlank()) {
			return "uploaded-notes.pdf";
		}
		String basename = filename.replace('\\', '/');
		basename = basename.substring(basename.lastIndexOf('/') + 1);
		StringBuilder safeName = new StringBuilder();
		basename.codePoints()
			.filter(codePoint -> !Character.isISOControl(codePoint))
			.limit(255)
			.forEach(safeName::appendCodePoint);
		String result = safeName.toString().strip();
		return result.isEmpty() ? "uploaded-notes.pdf" : result;
	}

	private void validateTopic(String topic) {
		if (topic == null || topic.isBlank() || topic.strip().length() > maxTopicCharacters) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Enter a topic of no more than " + maxTopicCharacters + " characters."
			);
		}
	}

	@Transactional
	public QuizAttemptResponse startAttempt(String studentEmail, Long materialId) {
		User student = findStudent(studentEmail);
		StudyMaterial material = studyMaterialRepository.findByIdAndStudent_IdAndDeletedAtIsNull(materialId, student.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study material was not found."));
		List<QuizConcept> materialConcepts = quizConceptRepository
			.findAllByMaterial_IdAndMaterial_DeletedAtIsNullOrderBySequenceNumberAsc(materialId);
		List<QuizConcept> learningConcepts = materialConcepts.stream()
			.filter(concept -> concept.getConsecutiveCorrect() < masteryTarget)
			.toList();
		List<QuizConcept> candidates = learningConcepts.isEmpty() ? materialConcepts : learningConcepts;
		List<QuizConcept> available = selectConcepts(candidates)
			.limit(questionCount)
			.toList();
		if (available.isEmpty()) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"No quiz concepts are available yet. Add and mark study material as studied first."
			);
		}

		List<GeminiQuizQuestionGenerator.ConceptInput> inputs = available.stream()
			.map(concept -> new GeminiQuizQuestionGenerator.ConceptInput(
				concept.getId(),
				concept.getSourceSentence(),
				concept.getExpectedAnswer()
			))
			.toList();
		List<GeminiQuizQuestionGenerator.GeneratedQuestion> generated =
			geminiQuizQuestionGenerator.generate(material.getTopic(), material.getSourceText(), inputs);
		if (generated.size() != available.size()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini returned an incomplete quiz. Please try again."
			);
		}

		QuizAttempt attempt = quizAttemptRepository.saveAndFlush(new QuizAttempt(student, available.size()));
		List<QuizAttemptQuestion> attemptQuestions = new ArrayList<>();
		for (int i = 0; i < available.size(); i++) {
			QuizConcept concept = available.get(i);
			GeminiQuizQuestionGenerator.GeneratedQuestion question = generated.get(i);
			if (!concept.getId().equals(question.conceptId())) {
				throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Gemini returned questions that do not match this study material. Please try again."
				);
			}
			attemptQuestions.add(new QuizAttemptQuestion(
				attempt,
				concept,
				i + 1,
				question.prompt(),
				question.options().get(0).text(),
				question.options().get(1).text(),
				question.options().get(2).text(),
				question.options().get(3).text(),
				question.correctOption(),
				"The supplied material states: \"" + question.sourceEvidence()
					+ "\" This supports \"" + concept.getExpectedAnswer() + "\" as the correct answer. "
					+ "Additional context, which may go beyond the supplied material: " + question.explanation()
			));
		}
		quizAttemptQuestionRepository.saveAllAndFlush(attemptQuestions);
		return toAttemptResponse(attempt, attemptQuestions);
	}

	@Transactional
	public QuizAnswerResponse submitAnswer(
		String studentEmail,
		Long attemptId,
		Long conceptId,
		String submittedAnswer
	) {
		User student = findStudent(studentEmail);
		QuizAttempt attempt = quizAttemptRepository.findByIdAndStudent_Id(attemptId, student.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz attempt was not found."));
		if (attempt.isCompleted()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This quiz attempt is already complete.");
		}

		QuizAttemptQuestion attemptQuestion = quizAttemptQuestionRepository
			.findByAttempt_IdAndConcept_Id(attemptId, conceptId)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"That concept is not part of this quiz attempt."
			));
		if (attemptQuestion.getCorrectOption() == null) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"This quiz was started before multiple-choice questions were enabled. Start a new quiz."
			);
		}
		if (quizAnswerRepository.existsByAttemptQuestion_Id(attemptQuestion.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This quiz question has already been answered.");
		}

		String answer = submittedAnswer.strip();
		if (!List.of("A", "B", "C", "D").contains(answer)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the available options.");
		}
		QuizConcept concept = attemptQuestion.getConcept();
		boolean correct = answer.equals(attemptQuestion.getCorrectOption());
		LocalDateTime answeredAt = LocalDateTime.now();
		quizAnswerRepository.saveAndFlush(new QuizAnswer(attemptQuestion, answer, correct));
		concept.recordAnswer(correct, answeredAt);
		if (correct) {
			attempt.recordCorrectAnswer();
		}
		if (quizAnswerRepository.countByAttemptQuestion_Attempt_Id(attemptId) == attempt.getTotalQuestions()) {
			attempt.complete(answeredAt);
		}

		return new QuizAnswerResponse(
			attempt.getId(),
			concept.getId(),
			correct,
			attemptQuestion.getCorrectOption(),
			attemptQuestion.getOption(attemptQuestion.getCorrectOption()),
			attemptQuestion.getExplanation(),
			attempt.getCorrectAnswers(),
			attempt.getTotalQuestions(),
			attempt.isCompleted(),
			status(concept, masteryTarget),
			concept.getConsecutiveCorrect(),
			masteryTarget
		);
	}

	private java.util.stream.Stream<QuizConcept> selectConcepts(List<QuizConcept> concepts) {
		Comparator<QuizConcept> leastRecentlyAttemptedFirst = Comparator
			.comparing(QuizConcept::getLastAttemptAt, Comparator.nullsFirst(Comparator.naturalOrder()));
		List<QuizConcept> untested = concepts.stream()
			.filter(concept -> concept.getTotalAttempts() == 0)
			.sorted(leastRecentlyAttemptedFirst)
			.toList();
		List<QuizConcept> needsRevision = concepts.stream()
			.filter(concept -> Boolean.FALSE.equals(concept.getLastCorrect()))
			.sorted(leastRecentlyAttemptedFirst)
			.toList();
		List<QuizConcept> inProgress = concepts.stream()
			.filter(concept -> concept.getTotalAttempts() > 0)
			.filter(concept -> !Boolean.FALSE.equals(concept.getLastCorrect()))
			.filter(concept -> concept.getConsecutiveCorrect() < masteryTarget)
			.sorted(leastRecentlyAttemptedFirst)
			.toList();
		List<QuizConcept> mastered = concepts.stream()
			.filter(concept -> concept.getConsecutiveCorrect() >= masteryTarget)
			.sorted(leastRecentlyAttemptedFirst)
			.toList();

		List<QuizConcept> ordered = new ArrayList<>();
		for (int index = 0; index < Math.max(untested.size(), needsRevision.size()); index++) {
			if (index < untested.size()) {
				ordered.add(untested.get(index));
			}
			if (index < needsRevision.size()) {
				ordered.add(needsRevision.get(index));
			}
		}
		ordered.addAll(inProgress);
		ordered.addAll(mastered);
		return ordered.stream().distinct();
	}

	private static int countStatus(List<QuizConceptProgressResponse> progress, String status) {
		return (int) progress.stream().filter(concept -> concept.status().equals(status)).count();
	}

	private static QuizConceptProgressResponse toProgress(QuizConcept concept, int masteryTarget) {
		return new QuizConceptProgressResponse(
			concept.getId(),
			concept.getMaterial().getId(),
			concept.getMaterial().getTopic(),
			status(concept, masteryTarget),
			concept.getConsecutiveCorrect(),
			masteryTarget,
			concept.getTotalAttempts()
		);
	}

	private static String status(QuizConcept concept, int masteryTarget) {
		if (concept.getConsecutiveCorrect() >= masteryTarget) {
			return "MASTERED";
		}
		if (concept.getTotalAttempts() == 0) {
			return "NOT_YET_TESTED";
		}
		if (Boolean.FALSE.equals(concept.getLastCorrect())) {
			return "NEEDS_REVISION";
		}
		return "IN_PROGRESS";
	}

	private static QuizAttemptResponse toAttemptResponse(
		QuizAttempt attempt,
		List<QuizAttemptQuestion> questions
	) {
		List<QuizQuestionResponse> responseQuestions = questions.stream()
			.map(question -> new QuizQuestionResponse(
				question.getConcept().getId(),
				question.getConcept().getMaterial().getTopic(),
				question.getQuestionPosition(),
				attempt.getTotalQuestions(),
				question.getQuestionPrompt(),
				List.of(
					new QuizOptionResponse("A", question.getOption("A")),
					new QuizOptionResponse("B", question.getOption("B")),
					new QuizOptionResponse("C", question.getOption("C")),
					new QuizOptionResponse("D", question.getOption("D"))
				)
			))
			.toList();
		return new QuizAttemptResponse(
			attempt.getId(),
			attempt.getTotalQuestions(),
			attempt.getCorrectAnswers(),
			attempt.isCompleted(),
			responseQuestions
		);
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}

}
