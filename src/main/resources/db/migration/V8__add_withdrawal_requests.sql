CREATE TABLE withdrawal_requests (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    reason_type   VARCHAR(50),
    reason_text   VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at  DATETIME,
    processed_by  BIGINT,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
