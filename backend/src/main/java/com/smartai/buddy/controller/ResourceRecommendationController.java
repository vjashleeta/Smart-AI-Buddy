package com.smartai.buddy.controller;

import com.smartai.buddy.dto.DifficultTopicRequest;
import com.smartai.buddy.dto.PdfTopicRecommendationResponse;
import com.smartai.buddy.dto.TopicRecommendationResponse;
import com.smartai.buddy.service.ResourceRecommendationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.smartai.buddy.dto.ApiError;

@RestController
@RequestMapping("/api/student/resources/recommendations")
public class ResourceRecommendationController {

	private final ResourceRecommendationService recommendationService;

	public ResourceRecommendationController(ResourceRecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@GetMapping
	public List<TopicRecommendationResponse> getRecommendations(
		@AuthenticationPrincipal UserDetails student
	) {
		return recommendationService.getRecommendations(student.getUsername());
	}

	@PostMapping
	public TopicRecommendationResponse recommendForReportedTopic(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody DifficultTopicRequest request
	) {
		return recommendationService.recommendForReportedTopic(request);
	}

	@PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public PdfTopicRecommendationResponse recommendFromPdf(
		@AuthenticationPrincipal UserDetails student,
		@RequestParam("file") MultipartFile file
	) {
		try {
			return recommendationService.recommendFromPdf(
				file.getOriginalFilename(),
				file.getContentType(),
				file.getBytes()
			);
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded PDF could not be read.");
		}
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handlePdfRequestFailure(ResponseStatusException exception) {
		String message = exception.getReason() == null
			? "The resource recommendation request could not be completed."
			: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message, Map.of()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public ApiError handleOversizedPdf() {
		return new ApiError("PDF files must be no larger than 20 MB.", Map.of());
	}
}
