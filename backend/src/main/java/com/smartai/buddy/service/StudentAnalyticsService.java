package com.smartai.buddy.service;

import com.smartai.buddy.dto.DashboardResponse;
import com.smartai.buddy.dto.PerformanceResponse;
import com.smartai.buddy.dto.QuizAttemptData;
import com.smartai.buddy.dto.QuizHistoryItem;
import com.smartai.buddy.dto.QuizTopicPerformance;
import com.smartai.buddy.dto.StudySessionResponse;
import com.smartai.buddy.dto.WeeklyActivityDay;
import com.smartai.buddy.entity.ChatMessage;
import com.smartai.buddy.entity.ChatMessageRole;
import com.smartai.buddy.entity.LearningActivity;
import com.smartai.buddy.entity.QuizAttempt;
import com.smartai.buddy.entity.StudyPlan;
import com.smartai.buddy.entity.StudySession;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.ChatMessageRepository;
import com.smartai.buddy.repository.LearningActivityRepository;
import com.smartai.buddy.repository.QuizAttemptRepository;
import com.smartai.buddy.repository.StudyPlanRepository;
import com.smartai.buddy.repository.StudySessionRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAnalyticsService {

	private static final int DASHBOARD_QUIZ_HISTORY_LIMIT = 5;
	private static final int WEEKLY_GOAL_MINUTES = 360;

	private final UserRepository userRepository;
	private final QuizAttemptRepository quizAttemptRepository;
	private final StudyPlanRepository studyPlanRepository;
	private final StudySessionRepository studySessionRepository;
	private final LearningActivityRepository learningActivityRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final Clock clock;

	public StudentAnalyticsService(
		UserRepository userRepository,
		QuizAttemptRepository quizAttemptRepository,
		StudyPlanRepository studyPlanRepository,
		StudySessionRepository studySessionRepository,
		LearningActivityRepository learningActivityRepository,
		ChatMessageRepository chatMessageRepository,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.quizAttemptRepository = quizAttemptRepository;
		this.studyPlanRepository = studyPlanRepository;
		this.studySessionRepository = studySessionRepository;
		this.learningActivityRepository = learningActivityRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardResponse getDashboard(String studentEmail) {
		User student = findStudent(studentEmail);
		long completedAttempts = quizAttemptRepository.countByStudent_IdAndCompletedAtIsNotNull(student.getId());
		List<QuizHistoryItem> recentAttempts = quizAttemptRepository
			.findCompletedAttemptDataByStudentId(
				student.getId(),
				PageRequest.of(0, DASHBOARD_QUIZ_HISTORY_LIMIT)
			)
			.stream()
			.map(StudentAnalyticsService::toHistoryItem)
			.toList();
		QuizHistoryItem latestAttempt = recentAttempts.isEmpty() ? null : recentAttempts.getFirst();
		Optional<StudyPlan> plan = studyPlanRepository.findByStudent_Id(student.getId());
		List<StudySession> upcomingSessions = plan.map(this::findUpcomingSessions).orElseGet(List::of);
		int upcomingSessionCount = plan.map(this::countUpcomingSessions).orElse(0);
		LearningTimeSummary learningTime = calculateLearningTime(student);
		return new DashboardResponse(
			student.getName(),
			Math.toIntExact(completedAttempts),
			quizAttemptRepository.findAverageScorePercentByStudentId(student.getId()),
			latestAttempt == null ? null : latestAttempt.scorePercent(),
			latestAttempt == null ? null : latestAttempt.correctAnswers(),
			latestAttempt == null ? null : latestAttempt.totalQuestions(),
			recentAttempts,
			upcomingSessions.stream().map(StudentAnalyticsService::toSessionResponse).toList(),
			upcomingSessionCount,
			learningTime.weekMinutes(),
			learningTime.currentStreakDays(),
			WEEKLY_GOAL_MINUTES,
			learningTime.dailyActivity()
		);
	}

	@Transactional(readOnly = true)
	public PerformanceResponse getPerformance(String studentEmail) {
		User student = findStudent(studentEmail);
		List<QuizAttemptData> attempts = quizAttemptRepository
			.findCompletedAttemptDataByStudentId(student.getId(), Pageable.unpaged());
		List<QuizHistoryItem> history = attempts.stream()
			.map(StudentAnalyticsService::toHistoryItem)
			.toList();
		QuizHistoryItem latestAttempt = history.isEmpty() ? null : history.getFirst();
		List<QuizTopicPerformance> topicPerformance = summarizeTopics(history);
		Optional<StudyPlan> plan = studyPlanRepository.findByStudent_Id(student.getId());
		LearningTimeSummary learningTime = calculateLearningTime(student);
		return new PerformanceResponse(
			Math.toIntExact(quizAttemptRepository.countByStudent_IdAndCompletedAtIsNotNull(student.getId())),
			quizAttemptRepository.findAverageScorePercentByStudentId(student.getId()),
			latestAttempt == null ? null : latestAttempt.scorePercent(),
			quizAttemptRepository.findBestScorePercentByStudentId(student.getId()),
			history,
			topicPerformance,
			plan.map(this::findUpcomingSessions).orElseGet(List::of).stream()
				.map(StudentAnalyticsService::toSessionResponse)
				.toList(),
			learningTime.weekMinutes(),
			learningTime.currentStreakDays(),
			WEEKLY_GOAL_MINUTES,
			learningTime.dailyActivity()
		);
	}

	private LearningTimeSummary calculateLearningTime(User student) {
		LocalDate today = LocalDate.now(clock);
		LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDateTime weekStart = monday.atStartOfDay();
		LocalDateTime weekEnd = monday.plusWeeks(1).atStartOfDay();
		List<TimeInterval> weeklyIntervals = new ArrayList<>();
		learningActivityRepository
			.findAllByStudent_IdAndStartedAtBeforeAndEndedAtAfterOrderByStartedAtAsc(
				student.getId(),
				weekEnd,
				weekStart
			)
			.forEach(activity -> addClippedInterval(
				weeklyIntervals,
				activity.getStartedAt(),
				activity.getEndedAt(),
				weekStart,
				weekEnd
			));
		studySessionRepository
			.findAllByPlan_Student_IdAndCompletedAtIsNotNullAndActualDurationMinutesGreaterThanAndActualStartedAtLessThanAndActualStartedAtGreaterThanOrderByActualStartedAtAsc(
				student.getId(),
				0,
				weekEnd,
				weekStart.minusDays(1)
			)
			.forEach(session -> addClippedInterval(
				weeklyIntervals,
				session.getActualStartedAt(),
				session.getActualStartedAt().plusMinutes(session.getActualDurationMinutes()),
				weekStart,
				weekEnd
			));

		Map<LocalDate, List<TimeInterval>> intervalsByDate = splitIntervalsByDate(weeklyIntervals);
		long weekSeconds = 0;
		List<WeeklyActivityDay> dailyActivity = new ArrayList<>(7);
		for (int offset = 0; offset < 7; offset++) {
			LocalDate date = monday.plusDays(offset);
			long daySeconds = mergedSeconds(intervalsByDate.getOrDefault(date, List.of()));
			weekSeconds += daySeconds;
			dailyActivity.add(new WeeklyActivityDay(date, Math.toIntExact(daySeconds / 60)));
		}

		Set<LocalDate> qualifyingDates = new TreeSet<>();
		learningActivityRepository.findAllByStudent_IdOrderByStartedAtAsc(student.getId())
			.stream()
			.filter(activity -> activity.getEndedAt().isAfter(activity.getStartedAt()))
			.forEach(activity -> addActivityDates(qualifyingDates, activity.getStartedAt(), activity.getEndedAt()));
		studySessionRepository
			.findAllByPlan_Student_IdAndCompletedAtIsNotNullAndActualDurationMinutesGreaterThanOrderByCompletedAtAsc(
				student.getId(),
				0
			)
			.stream()
			.filter(session -> session.getActualStartedAt() != null)
			.forEach(session -> addActivityDates(
				qualifyingDates,
				session.getActualStartedAt(),
				session.getActualStartedAt().plusMinutes(session.getActualDurationMinutes())
			));
		quizAttemptRepository.findAllByStudent_IdAndCompletedAtIsNotNullOrderByCompletedAtAsc(student.getId())
			.stream()
			.map(QuizAttempt::getCompletedAt)
			.map(LocalDateTime::toLocalDate)
			.forEach(qualifyingDates::add);
		chatMessageRepository.findAllByConversation_Student_IdAndRoleOrderByCreatedAtAsc(
				student.getId(),
				ChatMessageRole.ASSISTANT
			).stream()
			.map(ChatMessage::getCreatedAt)
			.map(LocalDateTime::toLocalDate)
			.forEach(qualifyingDates::add);

		return new LearningTimeSummary(
			Math.toIntExact(weekSeconds / 60),
			calculateCurrentStreak(qualifyingDates, today),
			List.copyOf(dailyActivity)
		);
	}

	private static void addClippedInterval(
		List<TimeInterval> intervals,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		LocalDateTime rangeStart,
		LocalDateTime rangeEnd
	) {
		if (startedAt == null || endedAt == null) {
			return;
		}
		LocalDateTime start = startedAt.isBefore(rangeStart) ? rangeStart : startedAt;
		LocalDateTime end = endedAt.isAfter(rangeEnd) ? rangeEnd : endedAt;
		if (end.isAfter(start)) {
			intervals.add(new TimeInterval(start, end));
		}
	}

	private static Map<LocalDate, List<TimeInterval>> splitIntervalsByDate(List<TimeInterval> intervals) {
		Map<LocalDate, List<TimeInterval>> intervalsByDate = new LinkedHashMap<>();
		for (TimeInterval interval : intervals) {
			LocalDateTime cursor = interval.start();
			while (cursor.isBefore(interval.end())) {
				LocalDateTime nextDay = cursor.toLocalDate().plusDays(1).atStartOfDay();
				LocalDateTime segmentEnd = interval.end().isBefore(nextDay) ? interval.end() : nextDay;
				intervalsByDate.computeIfAbsent(cursor.toLocalDate(), ignored -> new ArrayList<>())
					.add(new TimeInterval(cursor, segmentEnd));
				cursor = segmentEnd;
			}
		}
		return intervalsByDate;
	}

	private static long mergedSeconds(List<TimeInterval> intervals) {
		if (intervals.isEmpty()) {
			return 0;
		}
		List<TimeInterval> ordered = intervals.stream()
			.sorted(Comparator.comparing(TimeInterval::start))
			.toList();
		LocalDateTime currentStart = ordered.getFirst().start();
		LocalDateTime currentEnd = ordered.getFirst().end();
		long totalSeconds = 0;
		for (int index = 1; index < ordered.size(); index++) {
			TimeInterval next = ordered.get(index);
			if (!next.start().isAfter(currentEnd)) {
				if (next.end().isAfter(currentEnd)) {
					currentEnd = next.end();
				}
			} else {
				totalSeconds += Duration.between(currentStart, currentEnd).getSeconds();
				currentStart = next.start();
				currentEnd = next.end();
			}
		}
		return totalSeconds + Duration.between(currentStart, currentEnd).getSeconds();
	}

	private static void addActivityDates(
		Set<LocalDate> dates,
		LocalDateTime startedAt,
		LocalDateTime endedAt
	) {
		if (startedAt == null || endedAt == null || !endedAt.isAfter(startedAt)) {
			return;
		}
		LocalDate date = startedAt.toLocalDate();
		LocalDate lastDate = endedAt.minusNanos(1).toLocalDate();
		while (!date.isAfter(lastDate)) {
			dates.add(date);
			date = date.plusDays(1);
		}
	}

	private static int calculateCurrentStreak(Set<LocalDate> qualifyingDates, LocalDate today) {
		if (qualifyingDates.isEmpty()) {
			return 0;
		}
		LocalDate lastActivityDate = qualifyingDates.stream()
			.filter(date -> !date.isAfter(today))
			.max(LocalDate::compareTo)
			.orElse(null);
		if (lastActivityDate == null || lastActivityDate.isBefore(today.minusDays(1))) {
			return 0;
		}
		int streak = 0;
		LocalDate expectedDate = lastActivityDate;
		while (qualifyingDates.contains(expectedDate)) {
			streak++;
			expectedDate = expectedDate.minusDays(1);
		}
		return streak;
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}

	private List<StudySession> findUpcomingSessions(StudyPlan plan) {
		return studySessionRepository.findTop5ByPlan_IdAndSessionDateGreaterThanEqualOrderBySessionDateAscIdAsc(
			plan.getId(),
			LocalDate.now()
		);
	}

	private int countUpcomingSessions(StudyPlan plan) {
		return studySessionRepository.countByPlan_IdAndSessionDateGreaterThanEqual(plan.getId(), LocalDate.now());
	}

	private static QuizHistoryItem toHistoryItem(QuizAttemptData attempt) {
		return new QuizHistoryItem(
			attempt.attemptId(),
			attempt.topic(),
			attempt.completedAt(),
			attempt.correctAnswers(),
			attempt.totalQuestions(),
			scorePercent(attempt.correctAnswers(), attempt.totalQuestions())
		);
	}

	private static Double scorePercent(int correctAnswers, int totalQuestions) {
		if (totalQuestions <= 0 || correctAnswers < 0 || correctAnswers > totalQuestions) {
			return null;
		}
		return correctAnswers * 100.0 / totalQuestions;
	}

	private static List<QuizTopicPerformance> summarizeTopics(List<QuizHistoryItem> history) {
		Map<String, List<QuizHistoryItem>> attemptsByTopic = new LinkedHashMap<>();
		for (QuizHistoryItem attempt : history) {
			String topic = attempt.topic() == null || attempt.topic().isBlank()
				? "Uncategorized"
				: attempt.topic();
			attemptsByTopic.computeIfAbsent(topic, ignored -> new ArrayList<>()).add(attempt);
		}
		return attemptsByTopic.entrySet().stream()
			.map(entry -> {
				List<QuizHistoryItem> validScores = entry.getValue().stream()
					.filter(attempt -> attempt.scorePercent() != null)
					.toList();
				Double average = validScores.isEmpty()
					? null
					: validScores.stream().mapToDouble(QuizHistoryItem::scorePercent).average().orElseThrow();
				Double best = validScores.stream()
					.map(QuizHistoryItem::scorePercent)
					.max(Double::compareTo)
					.orElse(null);
				return new QuizTopicPerformance(entry.getKey(), entry.getValue().size(), average, best);
			})
			.sorted((left, right) -> {
				if (left.averageScorePercent() == null) {
					return right.averageScorePercent() == null ? left.topic().compareTo(right.topic()) : 1;
				}
				if (right.averageScorePercent() == null) {
					return -1;
				}
				int scoreOrder = Double.compare(left.averageScorePercent(), right.averageScorePercent());
				return scoreOrder == 0 ? left.topic().compareTo(right.topic()) : scoreOrder;
			})
			.toList();
	}

	private static StudySessionResponse toSessionResponse(StudySession session) {
		return new StudySessionResponse(
			session.getId(),
			session.getSubject(),
			session.getSessionDate(),
			session.getDurationMinutes(),
			session.getActualDurationMinutes(),
			session.getCompletedAt()
		);
	}

	private record TimeInterval(LocalDateTime start, LocalDateTime end) {
	}

	private record LearningTimeSummary(
		int weekMinutes,
		int currentStreakDays,
		List<WeeklyActivityDay> dailyActivity
	) {
	}
}
