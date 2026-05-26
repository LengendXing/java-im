-- IM Database Schema v0.1.0

CREATE DATABASE IF NOT EXISTS im_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE im_db;

-- 用户表
CREATE TABLE IF NOT EXISTS im_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(32) NOT NULL,
    nickname VARCHAR(64) DEFAULT '',
    avatar_url VARCHAR(256) DEFAULT '',
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB;

-- 好友关系表
CREATE TABLE IF NOT EXISTS im_friend (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0=申请中 1=已通过 2=已拒绝',
    message VARCHAR(128) DEFAULT '' COMMENT '申请留言',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_friend (friend_id),
    UNIQUE KEY uk_user_friend (user_id, friend_id)
) ENGINE=InnoDB;

-- 群组表
CREATE TABLE IF NOT EXISTS im_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    announcement VARCHAR(512) DEFAULT '',
    avatar_url VARCHAR(256) DEFAULT '',
    max_members INT DEFAULT 500,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB;

-- 群成员表
CREATE TABLE IF NOT EXISTS im_group_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role TINYINT DEFAULT 0 COMMENT '0=普通 1=管理员 2=群主',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB;

-- 消息表 (Phase 1 单表, 后续按月分库分表)
CREATE TABLE IF NOT EXISTS im_message (
    id BIGINT PRIMARY KEY COMMENT '雪花ID',
    session_id VARCHAR(64) NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT DEFAULT 0,
    group_id BIGINT DEFAULT 0,
    seq BIGINT NOT NULL,
    msg_type TINYINT NOT NULL COMMENT '1=文本 2=图片 3=文件 4=语音 5=系统',
    content TEXT,
    extra JSON,
    client_msg_id VARCHAR(64) DEFAULT '' COMMENT '客户端消息ID,幂等去重',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_seq (session_id, seq),
    INDEX idx_sender (sender_id),
    INDEX idx_client_msg_id (client_msg_id)
) ENGINE=InnoDB;

-- 会话表
CREATE TABLE IF NOT EXISTS im_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1=单聊 2=群聊',
    last_msg_id BIGINT DEFAULT 0,
    last_msg_content VARCHAR(256) DEFAULT '',
    last_msg_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    unread_count INT DEFAULT 0,
    is_top TINYINT DEFAULT 0,
    is_muted TINYINT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target_type (user_id, target_id, type),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB;
