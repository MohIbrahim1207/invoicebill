-- Flyway migration to ensure app_roles exists and app_user_roles FK points to app_roles(id)
-- This file is defensive: it will create app_roles if missing, migrate names from legacy app_role table,
-- reconcile app_user_roles.role_id values (when possible) and re-create FK.

CREATE TABLE IF NOT EXISTS app_roles (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  UNIQUE KEY uq_app_roles_name (name)
);

-- If legacy table `app_role` exists, copy role names into app_roles (avoid duplicates)
INSERT IGNORE INTO app_roles (name)
  SELECT name FROM app_role
  WHERE EXISTS (SELECT 1 FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='app_role');

-- If app_user_roles exist and reference ids that don't exist in app_roles, try to remap via legacy app_role table
-- (map by role name: old role id -> name -> new role id)
-- This UPDATE only runs when legacy `app_role` exists and there are mismatched role_id values.
UPDATE app_user_roles uur
JOIN app_role oldr ON uur.role_id = oldr.id
JOIN app_roles newr ON newr.name = oldr.name
SET uur.role_id = newr.id
WHERE EXISTS (SELECT 1 FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='app_role')
  AND NOT EXISTS (SELECT 1 FROM app_roles ar WHERE ar.id = uur.role_id);

-- Drop existing FK constraints on app_user_roles.role_id which reference app_roles or other role tables
-- and recreate a clean FK to app_roles(id).
-- We dynamically locate and drop FKs referencing this table for safety.
-- Drop any FK on app_user_roles.role_id
SET @fk := (
  SELECT CONSTRAINT_NAME
  FROM information_schema.KEY_COLUMN_USAGE k
  WHERE k.TABLE_SCHEMA = DATABASE()
    AND k.TABLE_NAME = 'app_user_roles'
    AND k.COLUMN_NAME = 'role_id'
    AND k.REFERENCED_TABLE_NAME IS NOT NULL
  LIMIT 1
);

SET @s = IF(@fk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE app_user_roles DROP FOREIGN KEY ', @fk));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Finally, add FK to app_roles(id) if not present
ALTER TABLE app_user_roles
  ADD CONSTRAINT fk_app_user_roles_role_id FOREIGN KEY (role_id) REFERENCES app_roles(id);

-- Safety: ensure app_roles has at least ADMIN/USER roles so seeder can rely on names
INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_USER');

