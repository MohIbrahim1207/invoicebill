-- Flyway migration to clean up duplicate users and add UNIQUE constraints to app_user table

-- 1. Re-link foreign key rows from duplicate username users to the original user (lowest ID)
UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.vendor_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET vt.vendor_id = u2.id;

UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.owner_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET vt.owner_id = u2.id;

UPDATE purchase_order po
JOIN app_user u1 ON po.vendor_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET po.vendor_id = u2.id;

UPDATE notification n
JOIN app_user u1 ON n.user_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET n.user_id = u2.id;

UPDATE client c
JOIN app_user u1 ON c.owner_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET c.owner_id = u2.id;

UPDATE ai_quote_conversion aq
JOIN app_user u1 ON aq.user_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id
SET aq.user_id = u2.id;


-- 2. Re-link foreign key rows from duplicate email users to the original user (lowest ID)
UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.vendor_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET vt.vendor_id = u2.id;

UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.owner_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET vt.owner_id = u2.id;

UPDATE purchase_order po
JOIN app_user u1 ON po.vendor_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET po.vendor_id = u2.id;

UPDATE notification n
JOIN app_user u1 ON n.user_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET n.user_id = u2.id;

UPDATE client c
JOIN app_user u1 ON c.owner_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET c.owner_id = u2.id;

UPDATE ai_quote_conversion aq
JOIN app_user u1 ON aq.user_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id
SET aq.user_id = u2.id;


-- 3. Re-link foreign key rows from duplicate gst_number users to the original user (lowest ID)
UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.vendor_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET vt.vendor_id = u2.id;

UPDATE vendor_ticket vt
JOIN app_user u1 ON vt.owner_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET vt.owner_id = u2.id;

UPDATE purchase_order po
JOIN app_user u1 ON po.vendor_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET po.vendor_id = u2.id;

UPDATE notification n
JOIN app_user u1 ON n.user_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET n.user_id = u2.id;

UPDATE client c
JOIN app_user u1 ON c.owner_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET c.owner_id = u2.id;

UPDATE ai_quote_conversion aq
JOIN app_user u1 ON aq.user_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id
SET aq.user_id = u2.id;


-- 4. Delete user roles for duplicate records
DELETE uur FROM app_user_roles uur
JOIN app_user u1 ON uur.user_id = u1.id
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id;

DELETE uur FROM app_user_roles uur
JOIN app_user u1 ON uur.user_id = u1.id
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id;

DELETE uur FROM app_user_roles uur
JOIN app_user u1 ON uur.user_id = u1.id
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id;


-- 5. Delete duplicate users
DELETE u1 FROM app_user u1
JOIN app_user u2 ON u1.username = u2.username AND u1.id > u2.id;

DELETE u1 FROM app_user u1
JOIN app_user u2 ON u1.email = u2.email AND u1.email IS NOT NULL AND u1.id > u2.id;

DELETE u1 FROM app_user u1
JOIN app_user u2 ON u1.gst_number = u2.gst_number AND u1.gst_number IS NOT NULL AND u1.id > u2.id;


-- 6. Add unique indexes to columns
ALTER TABLE app_user ADD UNIQUE INDEX uq_app_user_username (username);
ALTER TABLE app_user ADD UNIQUE INDEX uq_app_user_email (email);
ALTER TABLE app_user ADD UNIQUE INDEX uq_app_user_gst_number (gst_number);
