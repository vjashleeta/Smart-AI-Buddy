package com.smartai.buddy.controller;

import com.smartai.buddy.dto.DashboardResponse;
import com.smartai.buddy.dto.PerformanceResponse;
import com.smartai.buddy.service.StudentAnalyticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentAnalyticsController {

	private final StudentAnalyticsService analyticsService;

	public StudentAnalyticsController(StudentAnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/dashboard")
	public DashboardResponse dashboard(@AuthenticationPrincipal UserDetails student) {
		return analyticsService.getDashboard(student.getUsername());
	}

	@GetMapping("/performance")
	public PerformanceResponse performance(@AuthenticationPrincipal UserDetails student) {
		return analyticsService.getPerformance(student.getUsername());
	}
}
