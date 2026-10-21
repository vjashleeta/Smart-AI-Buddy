package com.smartai.buddy.service;

import com.smartai.buddy.dto.CreateStudySessionRequest;
import com.smartai.buddy.dto.PlannerResponse;
import com.smartai.buddy.dto.RecordActualStudyTimeRequest;
import com.smartai.buddy.dto.StudySessionResponse;
import com.smartai.buddy.entity.StudyPlan;
import com.smartai.buddy.entity.StudySession;
import com.smartai.buddy.entity.User;
import com.smartai.buddy.repository.StudyPlanRepository;
import com.smartai.buddy.repository.StudySessionRepository;
import com.smartai.buddy.repository.UserRepository;
import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlannerService {

	private final UserRepository userRepository;
	private final StudyPlanRepository studyPlanRepository;
	private final StudySessionRepository studySessionRepository;
	private final Clock clock;

	public PlannerService(
		UserRepository userRepository,
		StudyPlanRepository studyPlanRepository,
		StudySessionRepository studySessionRepository,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.studyPlanRepository = studyPlanRepository;
		this.studySessionRepository = studySessionRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PlannerResponse getPlanner(String studentEmail) {
		User student = findStudent(studentEmail);
		return studyPlanRepository.findByStudent_Id(student.getId())
			.map(plan -> new PlannerResponse(
				plan.getId(),
				studySessionRepository.findAllByPlan_IdOrderBySessionDateAscIdAsc(plan.getId())
					.stream()
					.map(PlannerService::toResponse)
					.toList()
			))
			.orElseGet(() -> new PlannerResponse(null, List.of()));
	}

	@Transactional
	public StudySessionResponse addSession(String studentEmail, CreateStudySessionRequest request) {
		User student = findStudent(studentEmail);
		StudyPlan plan = studyPlanRepository.findByStudent_Id(student.getId())
			.orElseGet(() -> studyPlanRepository.saveAndFlush(new StudyPlan(student)));
		StudySession session = studySessionRepository.saveAndFlush(new StudySession(
			plan,
			request.subject().strip(),
			request.sessionDate(),
			request.durationMinutes()
		));
		return toResponse(session);
	}

	@Transactional
	public StudySessionResponse recordActualTime(
		String studentEmail,
		Long sessionId,
		RecordActualStudyTimeRequest request
	) {
		User student = findStudent(studentEmail);
		StudySession session = studySessionRepository
			.findByIdAndPlan_Student_Id(sessionId, student.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study session was not found."));
		LocalDate today = LocalDate.now(clock);
		if (session.getSessionDate().isAfter(today)) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Actual time cannot be recorded for a future study session."
			);
		}
		validateActualDuration(session, request.actualDurationMinutes());
		session.recordActualDuration(request.actualDurationMinutes(), LocalDateTime.now(clock));
		return toResponse(studySessionRepository.saveAndFlush(session));
	}

	@Transactional
	public StudySessionResponse completeSession(
		String studentEmail,
		Long sessionId,
		RecordActualStudyTimeRequest request
	) {
		User student = findStudent(studentEmail);
		StudySession session = findOwnedSession(student.getId(), sessionId);
		if (session.getSessionDate().isAfter(LocalDate.now(clock))) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"A future study session cannot be marked as completed."
			);
		}
		validateActualDuration(session, request.actualDurationMinutes());
		if (session.getCompletedAt() == null) {
			if (!Integer.valueOf(request.actualDurationMinutes()).equals(session.getActualDurationMinutes())) {
				session.recordActualDuration(request.actualDurationMinutes(), LocalDateTime.now(clock));
			}
			session.markCompleted(LocalDateTime.now(clock));
		}
		return toResponse(studySessionRepository.saveAndFlush(session));
	}

	private StudySession findOwnedSession(Long studentId, Long sessionId) {
		return studySessionRepository
			.findByIdAndPlan_Student_Id(sessionId, studentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study session was not found."));
	}

	private static void validateActualDuration(StudySession session, int actualDurationMinutes) {
		if (actualDurationMinutes > session.getDurationMinutes()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Actual study time cannot exceed the planned session duration."
			);
		}
	}

	private User findStudent(String email) {
		return userRepository.findByCollegeEmail(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Authenticated student account is unavailable."
			));
	}

	private static StudySessionResponse toResponse(StudySession session) {
		return new StudySessionResponse(
			session.getId(),
			session.getSubject(),
			session.getSessionDate(),
			session.getDurationMinutes(),
			session.getActualDurationMinutes(),
			session.getCompletedAt()
		);
	}
}
