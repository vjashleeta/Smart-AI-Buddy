package com.smartai.buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "users",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_users_college_email",
		columnNames = "college_email"
	)
)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "college_email", nullable = false, length = 254)
	private String collegeEmail;

	@Column(name = "password_hash", nullable = false, length = 60)
	private String passwordHash;

	@Column(name = "email_verified", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT TRUE")
	private boolean emailVerified = true;

	protected User() {
	}

	public User(String name, String collegeEmail, String passwordHash) {
		this(name, collegeEmail, passwordHash, true);
	}

	public User(String name, String collegeEmail, String passwordHash, boolean emailVerified) {
		this.name = name;
		this.collegeEmail = collegeEmail;
		this.passwordHash = passwordHash;
		this.emailVerified = emailVerified;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCollegeEmail() {
		return collegeEmail;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void verifyEmail() {
		emailVerified = true;
	}

	public void updateName(String name) {
		this.name = name;
	}

	public void updatePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
