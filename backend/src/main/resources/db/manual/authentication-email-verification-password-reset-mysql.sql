-- Smart AI Buddy authentication schema migration (MySQL 8.0.46).
--
-- PRECONDITIONS - all must be confirmed by the operator before execution:
-- 1. Stop every running application instance.
-- 2. Create a fresh database backup and verify that it can be restored.
-- 3. Confirm the selected database is smart_ai_buddy.
-- 4. Confirm users is InnoDB and users.id is a signed BIGINT AUTO_INCREMENT
--    primary key. Both new user_id columns must match its signedness exactly.
-- 5. Confirm users.email_verified, email_verification_codes, and
--    password_reset_tokens are all absent. If any exists, stop and inspect
--    the schema; do not rerun or partially apply this script.
--
-- MySQL DDL implicitly commits. A failure can leave earlier statements
-- applied; inspect the schema and restore/reconcile manually rather than
-- blindly rerunning this migration.
--
-- Existing users are backfilled as verified. New registrations explicitly
-- insert email_verified = FALSE in RegistrationService.

ALTER TABLE users
	ADD COLUMN email_verified BOOLEAN NULL DEFAULT TRUE;

UPDATE users
	SET email_verified = TRUE
	WHERE email_verified IS NULL;

ALTER TABLE users
	MODIFY COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE email_verification_codes (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	purpose VARCHAR(32) NOT NULL,
	code_hash VARCHAR(60) NOT NULL,
	expires_at DATETIME(6) NOT NULL,
	attempts INT NOT NULL,
	resend_count INT NOT NULL,
	resend_window_started_at DATETIME(6) NOT NULL,
	last_sent_at DATETIME(6) NOT NULL,
	consumed_at DATETIME(6) NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_email_verification_user_purpose UNIQUE (user_id, purpose),
	CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	token_hash VARCHAR(64) NOT NULL,
	expires_at DATETIME(6) NOT NULL,
	consumed_at DATETIME(6) NULL,
	request_window_started_at DATETIME(6) NOT NULL,
	last_requested_at DATETIME(6) NOT NULL,
	request_count INT NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_password_reset_user UNIQUE (user_id),
	CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
