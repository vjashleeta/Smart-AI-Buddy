package com.smartai.buddy.exception;

import com.smartai.buddy.dto.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage())
		);
		return ResponseEntity.badRequest().body(new ApiError("Request details are invalid.", errors));
	}

	@ExceptionHandler(InvalidRegistrationException.class)
	public ResponseEntity<ApiError> handleInvalidRegistration(InvalidRegistrationException exception) {
		return ResponseEntity.badRequest().body(
			new ApiError(exception.getMessage(), Map.of("password", exception.getMessage()))
		);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
			new ApiError(exception.getMessage(), Map.of("email", exception.getMessage()))
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableRequest() {
		return ResponseEntity.badRequest().body(
			new ApiError("Request body must be valid JSON.", Map.of())
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
		String message = exception.getReason() == null
			? "The request could not be completed."
			: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message, Map.of()));
	}
}
