-- T0 schema proposal. MySQL 8.0+, separate empty database, never Signage's DB.
-- Not applied automatically; no credentials, demo seeds or personal data.
SET NAMES utf8mb4;
CREATE TABLE companies (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(160) NOT NULL,
 slug VARCHAR(80) NOT NULL UNIQUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE sites (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL,
 code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL, timezone VARCHAR(80) NOT NULL,
 UNIQUE(company_id,id), UNIQUE(company_id,code),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
CREATE TABLE areas (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL,
 code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
 UNIQUE(company_id,id), UNIQUE(company_id,site_id,id), UNIQUE(company_id,site_id,code),
 FOREIGN KEY(company_id,site_id) REFERENCES sites(company_id,id)
) ENGINE=InnoDB;
CREATE TABLE services (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
 prefix VARCHAR(8) NOT NULL, estimated_seconds INT UNSIGNED NOT NULL DEFAULT 600,
 UNIQUE(company_id,id), UNIQUE(company_id,code),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
CREATE TABLE queues (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 service_id BIGINT UNSIGNED NOT NULL, code VARCHAR(40) NOT NULL,
 policy ENUM('fifo','priority_fifo') NOT NULL DEFAULT 'fifo', enabled BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(company_id,id), UNIQUE(company_id,site_id,area_id,id), UNIQUE(company_id,area_id,code),
 FOREIGN KEY(company_id,site_id,area_id) REFERENCES areas(company_id,site_id,id),
 FOREIGN KEY(company_id,service_id) REFERENCES services(company_id,id)
) ENGINE=InnoDB;
CREATE TABLE counters (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 code VARCHAR(40) NOT NULL, label VARCHAR(80) NOT NULL,
 UNIQUE(company_id,id), UNIQUE(company_id,site_id,area_id,id), UNIQUE(company_id,area_id,code),
 FOREIGN KEY(company_id,site_id,area_id) REFERENCES areas(company_id,site_id,id)
) ENGINE=InnoDB;
CREATE TABLE counter_queues (
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 counter_id BIGINT UNSIGNED NOT NULL, queue_id BIGINT UNSIGNED NOT NULL,
 PRIMARY KEY(company_id,counter_id,queue_id),
 FOREIGN KEY(company_id,site_id,area_id,counter_id) REFERENCES counters(company_id,site_id,area_id,id),
 FOREIGN KEY(company_id,site_id,area_id,queue_id) REFERENCES queues(company_id,site_id,area_id,id)
) ENGINE=InnoDB;
CREATE TABLE users (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, login VARCHAR(160) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 display_name VARCHAR(160) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(company_id,id), UNIQUE(company_id,login),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
CREATE TABLE user_scopes (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL,
 role ENUM('company_admin','supervisor','operator','issuer','auditor') NOT NULL,
 site_id BIGINT UNSIGNED NULL, area_id BIGINT UNSIGNED NULL,
 FOREIGN KEY(company_id,user_id) REFERENCES users(company_id,id),
 FOREIGN KEY(company_id,site_id) REFERENCES sites(company_id,id),
 FOREIGN KEY(company_id,site_id,area_id) REFERENCES areas(company_id,site_id,id),
 CHECK(area_id IS NULL OR site_id IS NOT NULL)
) ENGINE=InnoDB;
CREATE TABLE ticket_counters (
 company_id BIGINT UNSIGNED NOT NULL, queue_id BIGINT UNSIGNED NOT NULL,
 business_date DATE NOT NULL, last_number INT UNSIGNED NOT NULL DEFAULT 0,
 PRIMARY KEY(company_id,queue_id,business_date),
 FOREIGN KEY(company_id,queue_id) REFERENCES queues(company_id,id)
) ENGINE=InnoDB;
CREATE TABLE tickets (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 queue_id BIGINT UNSIGNED NOT NULL, business_date DATE NOT NULL,
 number INT UNSIGNED NOT NULL, label VARCHAR(24) NOT NULL, priority SMALLINT NOT NULL DEFAULT 0,
 status ENUM('waiting','reserved','absent','called','serving','postponed','transferred','finished','cancelled') NOT NULL DEFAULT 'waiting',
 issued_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version INT UNSIGNED NOT NULL DEFAULT 1,
 UNIQUE(company_id,id), UNIQUE(company_id,site_id,area_id,id),
 UNIQUE(company_id,queue_id,business_date,number),
 INDEX next_ticket(company_id,area_id,queue_id,status,priority,issued_at,id),
 FOREIGN KEY(company_id,site_id,area_id,queue_id) REFERENCES queues(company_id,site_id,area_id,id)
) ENGINE=InnoDB;
CREATE TABLE calls (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 ticket_id BIGINT UNSIGNED NOT NULL, counter_id BIGINT UNSIGNED NOT NULL, operator_id BIGINT UNSIGNED NOT NULL,
 status ENUM('called','serving','finished','absent','cancelled','transferred') NOT NULL DEFAULT 'called',
 called_at DATETIME(6) NOT NULL, started_at DATETIME(6) NULL, finished_at DATETIME(6) NULL,
 -- At most one open call per ticket and per counter. MySQL unique indexes permit multiple NULL values.
 active_ticket_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status IN ('called','serving') THEN ticket_id ELSE NULL END) STORED,
 active_counter_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status IN ('called','serving') THEN counter_id ELSE NULL END) STORED,
 UNIQUE(company_id,id), UNIQUE(company_id,active_ticket_id), UNIQUE(company_id,active_counter_id),
 FOREIGN KEY(company_id,site_id,area_id,ticket_id) REFERENCES tickets(company_id,site_id,area_id,id),
 FOREIGN KEY(company_id,site_id,area_id,counter_id) REFERENCES counters(company_id,site_id,area_id,id),
 FOREIGN KEY(company_id,operator_id) REFERENCES users(company_id,id)
) ENGINE=InnoDB;
CREATE TABLE area_streams (
 company_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 sequence BIGINT UNSIGNED NOT NULL DEFAULT 0, call_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
 PRIMARY KEY(company_id,area_id),
 FOREIGN KEY(company_id,area_id) REFERENCES areas(company_id,id)
) ENGINE=InnoDB;
CREATE TABLE outbox_events (
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 company_id BIGINT UNSIGNED NOT NULL, site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 sequence BIGINT UNSIGNED NOT NULL, event_type VARCHAR(80) NOT NULL, envelope JSON NOT NULL,
 occurred_at DATETIME(6) NOT NULL, published_at DATETIME(6) NULL,
 attempts INT UNSIGNED NOT NULL DEFAULT 0, available_at DATETIME(6) NOT NULL,
 lease_until DATETIME(6) NULL, lease_owner VARCHAR(80) NULL, last_error_code VARCHAR(80) NULL,
 UNIQUE(company_id,area_id,sequence), INDEX dispatch(published_at,available_at,lease_until),
 FOREIGN KEY(company_id,site_id,area_id) REFERENCES areas(company_id,site_id,id)
) ENGINE=InnoDB;
CREATE TABLE idempotency_requests (
 company_id BIGINT UNSIGNED NOT NULL, principal_ref VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii NOT NULL, response_status SMALLINT UNSIGNED NOT NULL,
 response_json JSON NOT NULL, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY(company_id,principal_ref,operation_ref,request_key), INDEX expiry(expires_at),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
CREATE TABLE integrations (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, signage_company_ref VARCHAR(100) NOT NULL,
 credential_hash CHAR(64) CHARACTER SET ascii NOT NULL, revoked_at DATETIME(6) NULL,
 UNIQUE(company_id,id), UNIQUE(company_id,signage_company_ref),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
CREATE TABLE screen_bindings (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, integration_id BIGINT UNSIGNED NOT NULL,
 site_id BIGINT UNSIGNED NOT NULL, area_id BIGINT UNSIGNED NOT NULL,
 signage_screen_ref VARCHAR(100) NOT NULL, signage_zone_ref VARCHAR(100) NOT NULL,
 audio_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(company_id,integration_id,signage_screen_ref,signage_zone_ref),
 FOREIGN KEY(company_id,integration_id) REFERENCES integrations(company_id,id),
 FOREIGN KEY(company_id,site_id,area_id) REFERENCES areas(company_id,site_id,id)
) ENGINE=InnoDB;
CREATE TABLE audit_log (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 company_id BIGINT UNSIGNED NOT NULL, actor_ref VARCHAR(100) NOT NULL,
 action VARCHAR(80) NOT NULL, entity_ref VARCHAR(100) NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii NOT NULL, occurred_at DATETIME(6) NOT NULL,
 details JSON NOT NULL,
 INDEX history(company_id,occurred_at,id),
 FOREIGN KEY(company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
