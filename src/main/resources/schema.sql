-- =============================================
-- TiaoZhanBei 数据库初始化脚本 (PostgreSQL)
-- 说明：本项目使用 JPA 自动建表，此脚本仅供参考
-- =============================================

-- 创建数据库（如果还没创建的话）
-- CREATE DATABASE tiaozhanbei;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS t_user (
    id BIGSERIAL PRIMARY KEY,
    open_id VARCHAR(100) UNIQUE NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    phone VARCHAR(20),
    created_time TIMESTAMP,
    updated_time TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- =============================================
-- 2. 收藏表
-- =============================================
CREATE TABLE IF NOT EXISTS t_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    icon VARCHAR(100),
    content_type VARCHAR(50),
    content_id BIGINT,
    created_time TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- =============================================
-- 3. 咨询表
-- =============================================
CREATE TABLE IF NOT EXISTS t_consultation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lawyer_id BIGINT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    phone VARCHAR(20),
    type VARCHAR(50),
    status VARCHAR(50),
    created_time TIMESTAMP,
    updated_time TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);
-- =============================================
-- 4. 合同表
-- =============================================
CREATE TABLE IF NOT EXISTS t_contract (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_path VARCHAR(500),
    file_name VARCHAR(500),
    content TEXT,
    review_result TEXT,
    status VARCHAR(50),
    created_time TIMESTAMP,
    updated_time TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- =============================================
-- 5. 文书模板表
-- =============================================
CREATE TABLE IF NOT EXISTS t_document_template (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(100),
    content TEXT,
    file_path VARCHAR(500),
    download_count INTEGER DEFAULT 0,
    created_time TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- =============================================
-- 创建索引（可选，优化查询性能）
-- =============================================
CREATE INDEX IF NOT EXISTS idx_favorite_user_id ON t_favorite(user_id);
CREATE INDEX IF NOT EXISTS idx_consultation_user_id ON t_consultation(user_id);
CREATE INDEX IF NOT EXISTS idx_contract_user_id ON t_contract(user_id);
CREATE INDEX IF NOT EXISTS idx_template_category ON t_document_template(category);

-- =============================================
-- 插入一些测试数据（可选）
-- =============================================

-- 插入测试用户
-- INSERT INTO t_user (open_id, nickname, avatar, created_time, updated_time, is_deleted)
-- VALUES ('test_openid_001', '测试用户', 'https://example.com/avatar.png', NOW(), NOW(), FALSE);

-- 插入测试文书模板
-- INSERT INTO t_document_template (title, description, category, download_count, created_time, is_deleted)
-- VALUES 
-- ('民事起诉状', '适用于民事纠纷起诉', 'civil', 0, NOW(), FALSE),
-- ('离婚协议书', '离婚协议模板', 'civil', 0, NOW(), FALSE),
-- ('劳动合同', '劳动合同模板', 'contract', 0, NOW(), FALSE);

-- =============================================
-- 初始化完成！
-- =============================================
