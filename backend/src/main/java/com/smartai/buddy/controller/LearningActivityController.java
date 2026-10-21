package com.smartai.buddy.controller;

import com.smartai.buddy.dto.LearningActivitySessionResponse;
import com.smartai.buddy.dto.StartLearningActivityRequest;
import com.smartai.buddy.service.LearningActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/learning-activity/sessions")
public class LearningActivityController {

	private final LearningActivityService activityService;

	public LearningActivityController(LearningActivityService activityService) {
		this.activityService = activityService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LearningActivitySessionResponse start(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody StartLearningActivityRequest request
	) {
		return activityService.start(student.getUsername(), request);
	}

	@PutMapping("/{activityId}/heartbeat")
	public LearningActivitySessionResponse heartbeat(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long activityId
	) {
		return activityService.heartbeat(student.getUsername(), activityId);
	}

	@PostMapping("/{activityId}/stop")
	public LearningActivitySessionResponse stop(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long activityId
	) {
		return activityService.stop(student.getUsername(), activityId);
	}
}
