CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE chat_rooms (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_rooms_name (name),
    KEY idx_chat_rooms_created_by (created_by),
    CONSTRAINT fk_chat_rooms_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE room_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_room_user (room_id, user_id),
    KEY idx_membership_user_room (user_id, room_id),
    CONSTRAINT fk_membership_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE chat_messages (
    id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id VARCHAR(36) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_sender_client_id (sender_id, client_message_id),
    KEY idx_message_room_time_id (room_id, created_at, id),
    KEY idx_message_sender_time (sender_id, created_at),
    CONSTRAINT fk_message_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE message_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'SENT',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_message_user (message_id, user_id),
    KEY idx_receipt_user_state (user_id, state),
    CONSTRAINT fk_receipt_message FOREIGN KEY (message_id) REFERENCES chat_messages(id),
    CONSTRAINT fk_receipt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE chat_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_pending (published_at, id)
) ENGINE=InnoDB;
