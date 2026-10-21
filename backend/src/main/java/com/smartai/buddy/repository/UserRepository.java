package com.smartai.buddy.repository;

import com.smartai.buddy.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByCollegeEmail(String collegeEmail);

	Optional<User> findByCollegeEmail(String collegeEmail);

	void deleteByCollegeEmail(String collegeEmail);
}
