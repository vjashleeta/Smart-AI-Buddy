package com.smartai.buddy.dto;

import java.util.List;

public record PerformanceResponse(
	int completedQuizAttempts,
	Double averageScorePercent,
	Double latestScorePercent,
	Double bestScorePercent,
	List<QuizHistoryItem> scoreHistory,
	List<QuizTopicPerformance> topicPerformance,
	List<StudySessionResponse> upcomingPlannedSessions,
	Integer completedStudyMinutes,
	int currentStreakDays,
	int weeklyGoalMinutes,
	List<WeeklyActivityDay> weeklyActivity
) {
	public PerformanceResponse {
		scoreHistory = List.copyOf(scoreHistory);
		topicPerformance = List.copyOf(topicPerformance);
		upcomingPlannedSessions = List.copyOf(upcomingPlannedSessions);
		weeklyActivity = List.copyOf(weeklyActivity);
	}
}
