-- Reference MySQL 8.4 schema. Hibernate generates its own equivalent schema on startup.
-- Use only for a new dedicated database, not against a database containing data.
CREATE TABLE IF NOT EXISTS vault_accounts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 verified BOOLEAN NOT NULL DEFAULT FALSE, demo BOOLEAN NOT NULL DEFAULT FALSE, auth_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6)
);
CREATE TABLE IF NOT EXISTS vault_folders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL, parent_id BIGINT,
 name VARCHAR(150) NOT NULL, quick_access BOOLEAN NOT NULL DEFAULT FALSE, opened_at TIMESTAMP(6), created_at TIMESTAMP(6),
 INDEX idx_folder_owner(owner_id), INDEX idx_folder_parent(parent_id)
);
CREATE TABLE IF NOT EXISTS vault_files (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL, folder_id BIGINT,
 name VARCHAR(255) NOT NULL, object_key VARCHAR(80) NOT NULL UNIQUE, mime_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, favorite BOOLEAN NOT NULL DEFAULT FALSE, uploaded_at TIMESTAMP(6), deleted_at TIMESTAMP(6),
 INDEX idx_file_owner(owner_id), INDEX idx_file_folder(folder_id)
);
CREATE TABLE IF NOT EXISTS vault_shares (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, file_id BIGINT NOT NULL, owner_id BIGINT NOT NULL, recipient_id BIGINT,
 token_hash VARCHAR(64) UNIQUE, expires_at TIMESTAMP(6), created_at TIMESTAMP(6),
 INDEX idx_share_owner(owner_id), INDEX idx_share_recipient(recipient_id), INDEX idx_share_file(file_id)
);
CREATE TABLE IF NOT EXISTS vault_tokens (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE,
 purpose VARCHAR(20) NOT NULL, expires_at TIMESTAMP(6), INDEX idx_token_account(account_id)
);
