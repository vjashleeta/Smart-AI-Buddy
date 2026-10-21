package com.smartai.buddy.service;

import com.smartai.buddy.dto.LearningActivitySessionResponse;
import com.smartai.buddy.dto.StartLearningActivityRequest;
import com.smartai.buddy.entity.LearningActivity;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.LearningActivityRepository;
import com.smartai.buddy.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningActivityService {

	private final UserRepository userRepository;
	private final LearningActivityRepository activityRepository;
	private final Clock clock;

	public LearningActivityService(
		UserRepository userRepository,
		LearningActivityRepository activityRepository,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.activityRepository = activityRepository;
		this.clock = clock;
	}

	@Transactional
	public LearningActivitySessionResponse start(String studentEmail, StartLearningActivityRequest request) {
		User student = findStudent(studentEmail);
		LearningActivity activity = activityRepository.saveAndFlush(
			new LearningActivity(student, request.activityType(), LocalDateTime.now(clock))
		);
		return new LearningActivitySessionResponse(activity.getId(), true);
	}

	@Transactional
	public LearningActivitySessionResponse heartbeat(String studentEmail, Long activityId) {
		LearningActivity activity = findOwnedActivity(studentEmail, activityId);
		boolean active = activity.heartbeat(LocalDateTime.now(clock));
		activityRepository.saveAndFlush(activity);
		return new LearningActivitySessionResponse(activity.getId(), active);
	}

	@Transactional
	public LearningActivitySessionResponse stop(String studentEmail, Long activityId) {
		LearningActivity activity = findOwnedActivity(studentEmail, activityId);
		activity.stop(LocalDateTime.now(clock));
		activityRepository.saveAndFlush(activity);
		return new LearningActivitySessionResponse(activity.getId(), false);
	}

	private LearningActivity findOwnedActivity(String studentEmail, Long activityId) {
		User student = findStudent(studentEmail);
		return activityRepository.findOwnedForUpdate(activityId, student.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning activity was not found."));
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}
}
