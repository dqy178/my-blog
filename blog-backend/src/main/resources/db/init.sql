-- 个人博客数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS `blog_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `blog_db`;

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `bio` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    `motto` VARCHAR(200) DEFAULT NULL COMMENT '座右铭',
    `role` VARCHAR(20) DEFAULT 'ROLE_USER' COMMENT '角色',
    `status` TINYINT DEFAULT 1 COMMENT '状态 1:正常 0:禁用',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `security_question1` VARCHAR(100) DEFAULT NULL COMMENT '安全问题1',
    `security_answer1` VARCHAR(100) DEFAULT NULL COMMENT '安全问题1答案',
    `security_question2` VARCHAR(100) DEFAULT NULL COMMENT '安全问题2',
    `security_answer2` VARCHAR(100) DEFAULT NULL COMMENT '安全问题2答案',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 文章表
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `summary` VARCHAR(300) DEFAULT NULL COMMENT '摘要',
    `content` LONGTEXT COMMENT '内容',
    `cover` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态 0:草稿 1:发布 2:回收站',
    `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶 1:是 0:否',
    `view_count` BIGINT DEFAULT 0 COMMENT '浏览量',
    `like_count` BIGINT DEFAULT 0 COMMENT '点赞数',
    `comment_count` BIGINT DEFAULT 0 COMMENT '评论数',
    `allow_comment` TINYINT DEFAULT 1 COMMENT '允许评论 1:是 0:否',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_is_top` (`is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- 分类表
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID，用于层级',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `article_count` INT DEFAULT 0 COMMENT '文章数量',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- 对已存在但未升级的数据库，补充 parent_id 列（幂等）
DROP PROCEDURE IF EXISTS `add_category_parent_id_if_missing`;
DELIMITER //
CREATE PROCEDURE `add_category_parent_id_if_missing`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'category'
          AND COLUMN_NAME = 'parent_id'
    ) THEN
        ALTER TABLE `category` ADD COLUMN `parent_id` BIGINT DEFAULT NULL AFTER `sort`, ADD KEY `idx_parent_id` (`parent_id`);
    END IF;
END//
DELIMITER ;
CALL `add_category_parent_id_if_missing`();
DROP PROCEDURE `add_category_parent_id_if_missing`;

-- 标签表
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(30) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(20) DEFAULT '#409EFF' COMMENT '标签颜色',
    `article_count` INT DEFAULT 0 COMMENT '文章数量',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 文章标签关联表
DROP TABLE IF EXISTS `article_tag`;
CREATE TABLE `article_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签关联表';

-- 评论表
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `website` VARCHAR(200) DEFAULT NULL COMMENT '网站',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    `address` VARCHAR(100) DEFAULT NULL COMMENT '归属地',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0:待审核 1:通过 2:拒绝',
    `is_admin` TINYINT DEFAULT 0 COMMENT '是否管理员回复 1:是 0:否',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 友链表
DROP TABLE IF EXISTS `friend_link`;
CREATE TABLE `friend_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '友链名称',
    `url` VARCHAR(200) NOT NULL COMMENT '友链地址',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态 1:显示 0:隐藏',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链表';

-- 初始化数据
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `role`, `status`) VALUES
-- admin/123456 的 BCrypt 哈希（经 Spring Security BCryptPasswordEncoder 校验通过）
('admin', '$2a$10$X4us5m4e5CmlpKV7eL6TNO2M2dvAhP2mVZEpLNirFGcwbKAnMdU2e', '博主', 'admin@example.com', 'ROLE_ADMIN', 1);

INSERT INTO `category` (`name`, `description`, `sort`) VALUES
('技术分享', '分享技术相关文章', 1),
('生活随笔', '记录生活点滴', 2),
('读书笔记', '读书心得体会', 3);

INSERT INTO `tag` (`name`, `color`) VALUES
('Java', '#f89820'),
('Spring Boot', '#6db33f'),
('Vue', '#42b883'),
('MySQL', '#4479a1'),
('Redis', '#dc382d'),
('Docker', '#2496ed');

INSERT INTO `friend_link` (`name`, `url`, `description`, `sort`) VALUES
('Vue.js', 'https://vuejs.org/', '渐进式 JavaScript 框架', 1),
('Spring Boot', 'https://spring.io/projects/spring-boot', '简化 Spring 应用开发', 2);