-- Flyway migration to separate VENDOR and INTERNAL user authentication domains

-- 1. Add user_domain column to app_user table
ALTER TABLE app_user ADD COLUMN user_domain VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- 2. Update user_domain = 'VENDOR' for accounts holding ROLE_VENDOR
UPDATE app_user u
JOIN app_user_roles ur ON u.id = ur.user_id
JOIN app_roles r ON ur.role_id = r.id
SET u.user_domain = 'VENDOR'
WHERE r.name = 'ROLE_VENDOR';

-- 3. Update user_domain = 'INTERNAL' for accounts holding internal roles (EMPLOYEE, ADMIN, MANAGER, ACCOUNTS, SUPER_ADMIN)
UPDATE app_user u
JOIN app_user_roles ur ON u.id = ur.user_id
JOIN app_roles r ON ur.role_id = r.id
SET u.user_domain = 'INTERNAL'
WHERE r.name IN ('ROLE_EMPLOYEE', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_ACCOUNTS', 'ROLE_SUPER_ADMIN');

-- 4. Drop old single-column unique indexes if present
ALTER TABLE app_user DROP INDEX uq_app_user_username;
ALTER TABLE app_user DROP INDEX uq_app_user_email;

-- 5. Add composite unique indexes on (email, user_domain) and (username, user_domain)
ALTER TABLE app_user ADD UNIQUE INDEX uq_app_user_email_domain (email, user_domain);
ALTER TABLE app_user ADD UNIQUE INDEX uq_app_user_username_domain (username, user_domain);
