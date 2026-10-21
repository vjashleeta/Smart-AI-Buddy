package com.smartai.buddy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;

@Entity
@Table(
	name = "quiz_attempt_questions",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_quiz_attempt_position", columnNames = {"attempt_id", "question_position"}),
		@UniqueConstraint(name = "uk_quiz_attempt_concept", columnNames = {"attempt_id", "concept_id"})
	}
)
public class QuizAttemptQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attempt_id", nullable = false)
	private QuizAttempt attempt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "concept_id", nullable = false)
	private QuizConcept concept;

	@Column(name = "question_position", nullable = false)
	private int questionPosition;

	@Column(name = "question_prompt", length = 2000)
	private String questionPrompt;

	@Column(name = "option_a", length = 500)
	private String optionA;

	@Column(name = "option_b", length = 500)
	private String optionB;

	@Column(name = "option_c", length = 500)
	private String optionC;

	@Column(name = "option_d", length = 500)
	private String optionD;

	@Column(name = "correct_option", length = 1)
	private String correctOption;

	@Column(name = "explanation", length = 2000)
	private String explanation;

	protected QuizAttemptQuestion() {
	}

	public QuizAttemptQuestion(
		QuizAttempt attempt,
		QuizConcept concept,
		int questionPosition,
		String questionPrompt,
		String optionA,
		String optionB,
		String optionC,
		String optionD,
		String correctOption,
		String explanation
	) {
		this.attempt = attempt;
		this.concept = concept;
		this.questionPosition = questionPosition;
		this.questionPrompt = questionPrompt;
		this.optionA = optionA;
		this.optionB = optionB;
		this.optionC = optionC;
		this.optionD = optionD;
		this.correctOption = correctOption;
		this.explanation = explanation;
	}

	public Long getId() {
		return id;
	}

	public QuizAttempt getAttempt() {
		return attempt;
	}

	public QuizConcept getConcept() {
		return concept;
	}

	public int getQuestionPosition() {
		return questionPosition;
	}

	public String getQuestionPrompt() {
		return questionPrompt;
	}

	public String getOption(String label) {
		return switch (label) {
			case "A" -> optionA;
			case "B" -> optionB;
			case "C" -> optionC;
			case "D" -> optionD;
			default -> null;
		};
	}

	public String getCorrectOption() {
		return correctOption;
	}

	public String getExplanation() {
		return explanation;
	}
}
