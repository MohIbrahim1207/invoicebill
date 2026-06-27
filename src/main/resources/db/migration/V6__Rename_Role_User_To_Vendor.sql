-- Flyway migration to rename ROLE_USER to ROLE_VENDOR in the app_roles table.
-- First, ensure ROLE_VENDOR exists.
INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_VENDOR');

-- Remap associations in app_user_roles from ROLE_USER to ROLE_VENDOR.
-- Using UPDATE IGNORE in case some users already have both roles.
UPDATE IGNORE app_user_roles
SET role_id = (SELECT id FROM app_roles WHERE name = 'ROLE_VENDOR')
WHERE role_id = (SELECT id FROM app_roles WHERE name = 'ROLE_USER');

-- Delete ROLE_USER from the app_roles table.
DELETE FROM app_roles WHERE name = 'ROLE_USER';
