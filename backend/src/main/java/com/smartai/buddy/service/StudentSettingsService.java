package com.smartai.buddy.service;

import com.smartai.buddy.dto.ChangePasswordRequest;
import com.smartai.buddy.dto.StudentProfileResponse;
import com.smartai.buddy.dto.UpdateStudentProfileRequest;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentSettingsService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public StudentSettingsService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public StudentProfileResponse getProfile(String studentEmail) {
		return toProfile(findStudent(studentEmail));
	}

	@Transactional
	public StudentProfileResponse updateProfile(String studentEmail, UpdateStudentProfileRequest request) {
		User student = findStudent(studentEmail);
		String name = request.name().strip();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
		}
		student.updateName(name);
		return toProfile(userRepository.saveAndFlush(student));
	}

	@Transactional
	public void changePassword(String studentEmail, ChangePasswordRequest request) {
		User student = findStudent(studentEmail);
		if (!passwordEncoder.matches(request.currentPassword(), student.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
		}
		if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be no longer than 72 UTF-8 bytes.");
		}
		if (passwordEncoder.matches(request.newPassword(), student.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a new password different from your current password.");
		}
		student.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.saveAndFlush(student);
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}

	private static StudentProfileResponse toProfile(User student) {
		return new StudentProfileResponse(student.getName(), student.getCollegeEmail());
	}
}
