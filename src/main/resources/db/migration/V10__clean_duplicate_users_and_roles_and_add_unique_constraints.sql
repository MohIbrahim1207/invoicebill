-- Migration to clean duplicate roles and users, and add unique constraints

-- 1. Re-link foreign key rows from duplicate roles to the original role (lowest ID)
UPDATE app_user_roles aur
JOIN app_roles r1 ON aur.role_id = r1.id
JOIN app_roles r2 ON r1.name = r2.name AND r1.id > r2.id
SET aur.role_id = r2.id;

-- 2. Delete duplicate user roles for duplicate roles
DELETE aur FROM app_user_roles aur
JOIN app_roles r1 ON aur.role_id = r1.id
JOIN app_roles r2 ON r1.name = r2.name AND r1.id > r2.id;

-- 3. Delete duplicate roles
DELETE r1 FROM app_roles r1
JOIN app_roles r2 ON r1.name = r2.name AND r1.id > r2.id;

-- 4. Re-link foreign keys for duplicate username / email users to lowest ID user
UPDATE vendor_ticket vt JOIN app_user u1 ON vt.vendor_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET vt.vendor_id = u2.id;
UPDATE vendor_ticket vt JOIN app_user u1 ON vt.owner_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET vt.owner_id = u2.id;
UPDATE purchase_order po JOIN app_user u1 ON po.vendor_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET po.vendor_id = u2.id;
UPDATE notification n JOIN app_user u1 ON n.user_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET n.user_id = u2.id;
UPDATE client c JOIN app_user u1 ON c.owner_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET c.owner_id = u2.id;
UPDATE ai_quote_conversion aq JOIN app_user u1 ON aq.user_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET aq.user_id = u2.id;

UPDATE employee_tickets et JOIN app_user u1 ON et.employee_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET et.employee_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.assigned_manager_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET et.assigned_manager_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.reviewed_by_admin_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET et.reviewed_by_admin_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.accounts_user_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET et.accounts_user_id = u2.id;

UPDATE workflow_history wh JOIN app_user u1 ON wh.approved_by = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id SET wh.approved_by = u2.id;

UPDATE vendor_ticket vt JOIN app_user u1 ON vt.vendor_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET vt.vendor_id = u2.id;
UPDATE vendor_ticket vt JOIN app_user u1 ON vt.owner_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET vt.owner_id = u2.id;
UPDATE purchase_order po JOIN app_user u1 ON po.vendor_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET po.vendor_id = u2.id;
UPDATE notification n JOIN app_user u1 ON n.user_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET n.user_id = u2.id;
UPDATE client c JOIN app_user u1 ON c.owner_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET c.owner_id = u2.id;
UPDATE ai_quote_conversion aq JOIN app_user u1 ON aq.user_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET aq.user_id = u2.id;

UPDATE employee_tickets et JOIN app_user u1 ON et.employee_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET et.employee_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.assigned_manager_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET et.assigned_manager_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.reviewed_by_admin_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET et.reviewed_by_admin_id = u2.id;
UPDATE employee_tickets et JOIN app_user u1 ON et.accounts_user_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET et.accounts_user_id = u2.id;

UPDATE workflow_history wh JOIN app_user u1 ON wh.approved_by = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id SET wh.approved_by = u2.id;

-- 5. Delete user roles for duplicate records
DELETE uur FROM app_user_roles uur JOIN app_user u1 ON uur.user_id = u1.id JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id;
DELETE uur FROM app_user_roles uur JOIN app_user u1 ON uur.user_id = u1.id JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id;

-- 6. Delete duplicate users
DELETE u1 FROM app_user u1 JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id;
DELETE u1 FROM app_user u1 JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id;
