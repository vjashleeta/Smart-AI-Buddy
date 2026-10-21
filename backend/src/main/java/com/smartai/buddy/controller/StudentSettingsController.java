package com.smartai.buddy.controller;

import com.smartai.buddy.dto.ChangePasswordRequest;
import com.smartai.buddy.dto.StudentProfileResponse;
import com.smartai.buddy.dto.UpdateStudentProfileRequest;
import com.smartai.buddy.service.StudentSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/settings")
public class StudentSettingsController {

	private final StudentSettingsService settingsService;

	public StudentSettingsController(StudentSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping("/profile")
	public StudentProfileResponse getProfile(@AuthenticationPrincipal UserDetails student) {
		return settingsService.getProfile(student.getUsername());
	}

	@PutMapping("/profile")
	public StudentProfileResponse updateProfile(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody UpdateStudentProfileRequest request
	) {
		return settingsService.updateProfile(student.getUsername(), request);
	}

	@PostMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody ChangePasswordRequest request
	) {
		settingsService.changePassword(student.getUsername(), request);
	}
}
