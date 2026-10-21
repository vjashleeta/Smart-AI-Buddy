package com.smartai.buddy.controller;

import com.smartai.buddy.dto.AddQuizMaterialRequest;
import com.smartai.buddy.dto.ApiError;
import com.smartai.buddy.dto.QuizAnswerRequest;
import com.smartai.buddy.dto.QuizAnswerResponse;
import com.smartai.buddy.dto.QuizAttemptResponse;
import com.smartai.buddy.dto.QuizMaterialResponse;
import com.smartai.buddy.dto.QuizOverviewResponse;
import com.smartai.buddy.service.QuizService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/quiz")
public class QuizController {

	private final QuizService quizService;

	public QuizController(QuizService quizService) {
		this.quizService = quizService;
	}

	@GetMapping
	public QuizOverviewResponse overview(@AuthenticationPrincipal UserDetails student) {
		return quizService.getOverview(student.getUsername());
	}

	@PostMapping("/materials")
	@ResponseStatus(HttpStatus.CREATED)
	public QuizMaterialResponse addMaterial(
		@AuthenticationPrincipal UserDetails student,
		@Valid @RequestBody AddQuizMaterialRequest request
	) {
		return quizService.addMaterial(student.getUsername(), request);
	}

	@PostMapping(value = "/materials/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public QuizMaterialResponse addPdfMaterial(
		@AuthenticationPrincipal UserDetails student,
		@RequestParam("topic") String topic,
		@RequestParam("file") MultipartFile file
	) {
		try {
			return quizService.addPdfMaterial(
				student.getUsername(),
				topic,
				file.getOriginalFilename(),
				file.getContentType(),
				file.getBytes()
			);
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded PDF could not be read.");
		}
	}

	@DeleteMapping("/materials/{materialId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMaterial(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long materialId
	) {
		quizService.deleteMaterial(student.getUsername(), materialId);
	}

	@PostMapping("/attempts")
	public ResponseEntity<ApiError> startAttemptWithoutMaterial() {
		return ResponseEntity.badRequest().body(
			new ApiError("Select a study material before starting a quiz.", Map.of())
		);
	}

	@PostMapping("/materials/{materialId}/attempts")
	@ResponseStatus(HttpStatus.CREATED)
	public QuizAttemptResponse startAttempt(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long materialId
	) {
		return quizService.startAttempt(student.getUsername(), materialId);
	}

	@PostMapping("/attempts/{attemptId}/answers")
	public QuizAnswerResponse submitAnswer(
		@AuthenticationPrincipal UserDetails student,
		@PathVariable Long attemptId,
		@Valid @RequestBody QuizAnswerRequest request
	) {
		return quizService.submitAnswer(
			student.getUsername(),
			attemptId,
			request.conceptId(),
			request.answer()
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleQuizRequestFailure(ResponseStatusException exception) {
		String message = exception.getReason() == null
			? "The quiz request could not be completed."
			: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message, Map.of()));
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiError> handleStudyDataPersistenceFailure() {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
			new ApiError("The study request could not be completed. Please try again.", Map.of())
		);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiError> handleOversizedPdf() {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
			new ApiError("PDF files must be no larger than 20 MB.", Map.of())
		);
	}
}
