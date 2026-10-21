package com.smartai.buddy.dto;

import java.util.List;

public record DashboardResponse(
	String studentName,
	int completedQuizAttempts,
	Double averageScorePercent,
	Double latestScorePercent,
	Integer latestCorrectAnswers,
	Integer latestTotalQuestions,
	List<QuizHistoryItem> recentQuizAttempts,
	List<StudySessionResponse> upcomingPlannedSessions,
	int upcomingPlannedSessionCount,
	Integer completedStudyMinutes,
	int currentStreakDays,
	int weeklyGoalMinutes,
	List<WeeklyActivityDay> weeklyActivity
) {
	public DashboardResponse {
		recentQuizAttempts = List.copyOf(recentQuizAttempts);
		upcomingPlannedSessions = List.copyOf(upcomingPlannedSessions);
		weeklyActivity = List.copyOf(weeklyActivity);
	}
}
