package com.smartai.buddy.repository;

import com.smartai.buddy.entity.EmailVerificationCode;
import com.smartai.buddy.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select code from EmailVerificationCode code
		where code.user.id = :userId and code.purpose = :purpose
		""")
	Optional<EmailVerificationCode> findForUpdate(
		@Param("userId") Long userId,
		@Param("purpose") VerificationPurpose purpose
	);

	void deleteByUser_Id(Long userId);
}
