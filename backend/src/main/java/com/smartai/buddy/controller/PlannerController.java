package com.smartai.buddy.controller;

import com.smartai.buddy.dto.CreateStudySessionRequest;
import com.smartai.buddy.dto.PlannerResponse;
import com.smartai.buddy.dto.RecordActualStudyTimeRequest;
import com.smartai.buddy.dto.StudySessionResponse;
import com.smartai.buddy.service.PlannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/planner")
public class PlannerController {

	private final PlannerService plannerService;

	public PlannerController(PlannerService plannerService) {
		this.plannerService = plannerService;
	}

	@GetMapping
	public PlannerResponse getPlanner(@AuthenticationPrincipal UserDetails student) {
		return plannerService.getPlanner(student.getUsername());
	}

	@PostMapping("/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public StudySessionResponse addSession(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody CreateStudySessionRequest request
	) {
		return plannerService.addSession(student.getUsername(), request);
	}

	@PutMapping("/sessions/{sessionId}/actual-time")
	public StudySessionResponse recordActualTime(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long sessionId,
		@Valid @RequestBody RecordActualStudyTimeRequest request
	) {
		return plannerService.recordActualTime(student.getUsername(), sessionId, request);
	}

	@PutMapping("/sessions/{sessionId}/complete")
	public StudySessionResponse completeSession(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long sessionId,
		@Valid @RequestBody RecordActualStudyTimeRequest request
	) {
		return plannerService.completeSession(student.getUsername(), sessionId, request);
	}
}
