package com.billing.invoicehub.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Component
@Order(0)
public class StartupRoleMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Logger log = LoggerFactory.getLogger(StartupRoleMigration.class);

    public StartupRoleMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Running startup role migration checks...");

            try {
                jdbc.execute("ALTER TABLE vendor_ticket MODIFY COLUMN status_request ENUM('OPEN', 'IN_PROGRESS', 'REVISE', 'PARTIALLY_PAID', 'RESOLVED', 'CANCEL')");
                log.info("Successfully updated vendor_ticket.status_request enum values");
            } catch (Exception e) {
                log.debug("Could not alter vendor_ticket.status_request (might be non-mysql or column missing): {}", e.getMessage());
            }
            try {
                jdbc.execute("ALTER TABLE vendor_ticket_history MODIFY COLUMN status ENUM('OPEN', 'IN_PROGRESS', 'REVISE', 'PARTIALLY_PAID', 'RESOLVED', 'CANCEL')");
                log.info("Successfully updated vendor_ticket_history.status enum values");
            } catch (Exception e) {
                log.debug("Could not alter vendor_ticket_history.status (might be non-mysql or column missing): {}", e.getMessage());
            }

            try {
                jdbc.execute("ALTER TABLE app_user DROP COLUMN reminder24h_sent");
                log.info("Successfully dropped column reminder24h_sent from app_user");
            } catch (Exception e) {
                log.debug("Column reminder24h_sent already dropped or not present");
            }
            try {
                jdbc.execute("ALTER TABLE app_user DROP COLUMN reminder48h_sent");
                log.info("Successfully dropped column reminder48h_sent from app_user");
            } catch (Exception e) {
                log.debug("Column reminder48h_sent already dropped or not present");
            }
            try {
                jdbc.execute("ALTER TABLE app_user DROP COLUMN last_reminder_sent_at");
                log.info("Successfully dropped column last_reminder_sent_at from app_user");
            } catch (Exception e) {
                log.debug("Column last_reminder_sent_at already dropped or not present");
            }

            try {
                jdbc.execute("DELETE FROM app_roles WHERE name = 'ROLE_MANAGER'");
                log.info("Successfully removed ROLE_MANAGER from app_roles");
            } catch (Exception e) {
                log.debug("Could not delete ROLE_MANAGER from app_roles: {}", e.getMessage());
            }

            try {
                jdbc.execute("ALTER TABLE notification MODIFY COLUMN type VARCHAR(50)");
                log.info("Successfully updated notification.type to VARCHAR(50)");
            } catch (Exception e) {
                log.debug("Could not alter notification.type (might be non-mysql or column missing): {}", e.getMessage());
            }

            // Ensure app_roles table exists
            jdbc.execute("CREATE TABLE IF NOT EXISTS app_roles (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, UNIQUE KEY uq_app_roles_name (name))");

            // Ensure core roles are present
            try {
                jdbc.update("INSERT IGNORE INTO app_roles (name) VALUES (?)", "ROLE_ADMIN");
                jdbc.update("INSERT IGNORE INTO app_roles (name) VALUES (?)", "ROLE_VENDOR");
            } catch (Exception ex) {
                // Some drivers don't support INSERT IGNORE; fall back to existence-check insert
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ?", Integer.class, "ROLE_ADMIN");
                if (count == 0) jdbc.update("INSERT INTO app_roles (name) VALUES (?)", "ROLE_ADMIN");
                count = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ?", Integer.class, "ROLE_VENDOR");
                if (count == 0) jdbc.update("INSERT INTO app_roles (name) VALUES (?)", "ROLE_VENDOR");
            }

            // Migrate any existing ROLE_USER to ROLE_VENDOR in the database automatically on startup
            try {
                Long vendorRoleId = null;
                Long userRoleId = null;
                try {
                    vendorRoleId = jdbc.queryForObject("SELECT MIN(id) FROM app_roles WHERE name = ?", Long.class, "ROLE_VENDOR");
                } catch (Exception e) {}
                try {
                    userRoleId = jdbc.queryForObject("SELECT MIN(id) FROM app_roles WHERE name = ?", Long.class, "ROLE_USER");
                } catch (Exception e) {}

                if (vendorRoleId != null && userRoleId != null) {
                    log.info("Migrating existing user roles from ROLE_USER (id: {}) to ROLE_VENDOR (id: {})...", userRoleId, vendorRoleId);
                    List<Map<String, Object>> userRoles = jdbc.queryForList("SELECT user_id FROM app_user_roles WHERE role_id = ?", userRoleId);
                    for (Map<String, Object> ur : userRoles) {
                        Long userId = ((Number) ur.get("user_id")).longValue();
                        Integer hasVendor = jdbc.queryForObject("SELECT COUNT(*) FROM app_user_roles WHERE user_id = ? AND role_id = ?", Integer.class, userId, vendorRoleId);
                        if (hasVendor == null || hasVendor == 0) {
                            jdbc.update("UPDATE app_user_roles SET role_id = ? WHERE user_id = ? AND role_id = ?", vendorRoleId, userId, userRoleId);
                        } else {
                            jdbc.update("DELETE FROM app_user_roles WHERE user_id = ? AND role_id = ?", userId, userRoleId);
                        }
                    }
                    jdbc.update("DELETE FROM app_roles WHERE id = ?", userRoleId);
                    log.info("Successfully migrated user roles from ROLE_USER to ROLE_VENDOR.");
                }
            } catch (Exception ex) {
                log.warn("Failed migrating ROLE_USER to ROLE_VENDOR on startup: {}", ex.getMessage());
            }

            // Deduplicate app_roles by name: keep the lowest id, remap app_user_roles to it, delete duplicates
            List<Map<String,Object>> duplicates = jdbc.queryForList(
                "SELECT name FROM app_roles GROUP BY name HAVING COUNT(*) > 1"
            );
            for (Map<String,Object> dup : duplicates) {
                String name = (String) dup.get("name");
                List<Long> ids = jdbc.queryForList("SELECT id FROM app_roles WHERE name = ? ORDER BY id", Long.class, name);
                if (ids.size() <= 1) continue;
                Long keeper = ids.get(0);
                List<Long> others = ids.subList(1, ids.size());
                // remap user-role links
                for (Long otherId : others) {
                    try {
                        jdbc.update("UPDATE app_user_roles SET role_id = ? WHERE role_id = ?", keeper, otherId);
                    } catch (Exception ex) {
                        log.debug("Failed remapping app_user_roles {} -> {}: {}", otherId, keeper, ex.getMessage());
                    }
                }
                // delete duplicate role rows
                try {
                    String inClause = String.join(",", others.stream().map(Object::toString).toArray(String[]::new));
                    jdbc.execute("DELETE FROM app_roles WHERE id IN (" + inClause + ")");
                } catch (Exception ex) {
                    log.debug("Failed deleting duplicate app_roles for name {}: {}", name, ex.getMessage());
                }
            }

            // Ensure unique index on name exists (after deduplication)
            try {
                jdbc.execute("ALTER TABLE app_roles ADD UNIQUE INDEX uq_app_roles_name (name)");
            } catch (Exception ex) {
                // ignore if already exists or cannot be added
                log.debug("Could not add unique index on app_roles.name: {}", ex.getMessage());
            }

            // If legacy app_role table exists, copy names and remap user-role ids
            Integer legacyExists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_role'", Integer.class);
            if (legacyExists != null && legacyExists > 0) {
                log.info("Found legacy table 'app_role' - attempting to migrate names and remap user-role ids");
                // copy names
                try {
                    jdbc.update("INSERT IGNORE INTO app_roles (name) SELECT name FROM app_role");
                } catch (Exception ex) {
                    // fallback: iterate rows
                    List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, name FROM app_role");
                    for (Map<String,Object> r: rows) {
                        String name = (String)r.get("name");
                        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ?", Integer.class, name);
                        if (cnt == 0) jdbc.update("INSERT INTO app_roles (name) VALUES (?)", name);
                    }
                }

                // remap app_user_roles.role_id from old ids to new ids by name
                List<Map<String,Object>> oldRoles = jdbc.queryForList("SELECT id, name FROM app_role");
                for (Map<String,Object> r: oldRoles) {
                    Long oldId = ((Number)r.get("id")).longValue();
                    String name = (String)r.get("name");
                    Long newId = jdbc.queryForObject("SELECT id FROM app_roles WHERE name = ? LIMIT 1", Long.class, name);
                    if (newId != null) {
                        try {
                            jdbc.update("UPDATE app_user_roles SET role_id = ? WHERE role_id = ?", newId, oldId);
                        } catch (Exception ex) {
                            log.debug("Failed to remap role_id {} -> {} : {}", oldId, newId, ex.getMessage());
                        }
                    }
                }
            }

            // If app_user_roles table exists, drop any foreign key on role_id and recreate it pointing to app_roles(id)
            Integer joinExists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user_roles'", Integer.class);
            if (joinExists != null && joinExists > 0) {
                List<String> fks = jdbc.queryForList(
                    "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user_roles' AND COLUMN_NAME = 'role_id' AND REFERENCED_TABLE_NAME IS NOT NULL",
                    String.class
                );
                for (String fk: fks) {
                    try {
                        jdbc.execute("ALTER TABLE app_user_roles DROP FOREIGN KEY `" + fk + "`");
                    } catch (Exception ex) {
                        log.debug("Could not drop FK {}: {}", fk, ex.getMessage());
                    }
                }

                // Add FK if it's not present
                try {
                    jdbc.execute("ALTER TABLE app_user_roles ADD CONSTRAINT fk_app_user_roles_role_id FOREIGN KEY (role_id) REFERENCES app_roles(id)");
                } catch (Exception ex) {
                    log.debug("Could not add FK to app_user_roles.role_id: {}", ex.getMessage());
                }
            }

            // -------------------------------------------------------------------------
            // Domain Separation & Security Migration:
            // Ensure app_user columns exist safely without using "ADD COLUMN IF NOT EXISTS"
            // (MySQL 8 does NOT support IF NOT EXISTS on ADD COLUMN; check information_schema instead).
            // -------------------------------------------------------------------------
            addColumnIfNotExists("app_user", "user_domain", "VARCHAR(20) NOT NULL DEFAULT 'INTERNAL'");
            addColumnIfNotExists("app_user", "force_password_change", "BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfNotExists("app_user", "failed_login_attempts", "INT NOT NULL DEFAULT 0");
            addColumnIfNotExists("app_user", "lockout_expiry", "DATETIME NULL");
            addColumnIfNotExists("app_user", "two_factor_enabled", "BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfNotExists("app_user", "two_factor_secret", "VARCHAR(255) NULL");
            addColumnIfNotExists("app_user", "last_seen_version", "VARCHAR(50) NULL");

            // Ensure system_settings table exists for maintenance banners and configuration
            try {
                jdbc.execute("CREATE TABLE IF NOT EXISTS system_settings (" +
                        "setting_key VARCHAR(100) NOT NULL PRIMARY KEY, " +
                        "setting_value TEXT, " +
                        "updated_at DATETIME NULL, " +
                        "updated_by VARCHAR(100) NULL)");
                log.debug("Verified system_settings table exists.");
            } catch (Exception ex) {
                log.warn("system_settings table verification: {}", ex.getMessage());
            }

            // 2. Assign user_domain = 'VENDOR' for all accounts holding ROLE_VENDOR
            try {
                int vendorsUpdated = jdbc.update(
                    "UPDATE app_user SET user_domain = 'VENDOR' " +
                    "WHERE id IN (" +
                    "    SELECT ur.user_id FROM app_user_roles ur " +
                    "    JOIN app_roles r ON ur.role_id = r.id " +
                    "    WHERE r.name = 'ROLE_VENDOR'" +
                    ") AND (user_domain IS NULL OR user_domain != 'VENDOR')"
                );
                if (vendorsUpdated > 0) {
                    log.info("Migrated {} existing vendor accounts to VENDOR domain.", vendorsUpdated);
                }
            } catch (Exception ex) {
                log.warn("Failed migrating accounts to VENDOR domain: {}", ex.getMessage());
            }

            // 3. Fallback: classify vendor accounts by business profile attributes
            try {
                int fallbackVendors = jdbc.update(
                    "UPDATE app_user SET user_domain = 'VENDOR' " +
                    "WHERE (company_name IS NOT NULL OR gst_number IS NOT NULL) " +
                    "  AND (user_domain IS NULL OR user_domain != 'VENDOR') " +
                    "  AND NOT EXISTS (" +
                    "      SELECT 1 FROM app_user_roles ur " +
                    "      JOIN app_roles r ON ur.role_id = r.id " +
                    "      WHERE ur.user_id = app_user.id " +
                    "        AND r.name IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_ACCOUNTS', 'ROLE_MANAGER', 'ROLE_EMPLOYEE')" +
                    "  )"
                );
                if (fallbackVendors > 0) {
                    log.info("Migrated {} vendor accounts to VENDOR domain via profile attributes.", fallbackVendors);
                }
            } catch (Exception ex) {
                log.debug("Vendor fallback profile check: {}", ex.getMessage());
            }

            // 4. Assign user_domain = 'INTERNAL' for all accounts holding internal roles
            try {
                int internalsUpdated = jdbc.update(
                    "UPDATE app_user SET user_domain = 'INTERNAL' " +
                    "WHERE id IN (" +
                    "    SELECT ur.user_id FROM app_user_roles ur " +
                    "    JOIN app_roles r ON ur.role_id = r.id " +
                    "    WHERE r.name IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_ACCOUNTS', 'ROLE_MANAGER', 'ROLE_EMPLOYEE')" +
                    ") AND (user_domain IS NULL OR user_domain != 'INTERNAL')"
                );
                if (internalsUpdated > 0) {
                    log.info("Migrated {} existing accounts to INTERNAL domain.", internalsUpdated);
                }
            } catch (Exception ex) {
                log.warn("Failed migrating accounts to INTERNAL domain: {}", ex.getMessage());
            }

            // 5. Default any remaining records without domain to 'INTERNAL'
            try {
                int nullDomainCount = jdbc.update(
                    "UPDATE app_user SET user_domain = 'INTERNAL' WHERE user_domain IS NULL OR user_domain = ''"
                );
                if (nullDomainCount > 0) {
                    log.info("Defaulted {} user records with empty domain to INTERNAL.", nullDomainCount);
                }
            } catch (Exception ex) {
                log.debug("User domain null fallback: {}", ex.getMessage());
            }

            // 6. Safe index creation for domain lookup performance (checks information_schema to prevent duplicate key errors)
            createIndexIfNotExists("idx_app_user_domain", "app_user", "user_domain");
            createIndexIfNotExists("idx_app_user_usr_dom", "app_user", "username, user_domain");
            createIndexIfNotExists("idx_app_user_email_dom", "app_user", "email, user_domain");

            // Normalize empty and placeholder gst_number strings to NULL before creating unique index
            try {
                int cleanedGst = jdbc.update(
                    "UPDATE app_user SET gst_number = NULL " +
                    "WHERE gst_number IS NOT NULL AND (" +
                    "   TRIM(gst_number) = '' " +
                    "   OR UPPER(TRIM(gst_number)) IN (" +
                    "       '-', '--', '0', '00', 'NO PPN', 'NOPPN', 'NO-PPN', " +
                    "       'NON PKP', 'NON-PKP', 'NONPKP', 'NA', 'N/A', 'NOT APPLICABLE', " +
                    "       'NONE', 'NIL', 'NULL', 'TIDAK ADA', 'BELUM ADA', 'TIDAK PUNYA'" +
                    "   )" +
                    ")"
                );
                if (cleanedGst > 0) {
                    log.info("Normalized {} placeholder/empty GST numbers to NULL in app_user.", cleanedGst);
                }
            } catch (Exception ex) {
                log.warn("GST placeholder cleanup note: {}", ex.getMessage());
            }
            createUniqueIndexIfNotExists("uq_app_user_gst_number", "app_user", "gst_number");
            createLowerEmailUniqueIndexIfNotExists();

            // Version 1.6.0: Ensure FK column invoice_id exists on vendor_ticket and vendor_ticket_id on invoice
            addColumnIfNotExists("vendor_ticket", "invoice_id", "BIGINT NULL");
            dropColumnIfExists("invoice", "vendor_ticket_id");
            migrateInvoiceDateColumn();
            backfillVendorTicketsAndInvoices();

            log.info("Startup role migration completed.");
        } catch (Exception ex) {
            log.warn("Startup role migration failed - continuing startup. Cause: {}", ex.getMessage());
        }
    }

    /**
     * Checks if a column exists in the database by querying information_schema.COLUMNS.
     * Compatible with both MySQL and H2 without throwing exceptions or relying on MariaDB-only syntax.
     */
    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ? " +
                "AND (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC')",
                Integer.class,
                tableName.toLowerCase(),
                columnName.toLowerCase()
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            try {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?",
                    Integer.class,
                    tableName.toLowerCase(),
                    columnName.toLowerCase()
                );
                return count != null && count > 0;
            } catch (Exception e) {
                log.warn("Could not query information_schema.COLUMNS for {}.{}: {}", tableName, columnName, e.getMessage());
                return false;
            }
        }
    }

    /**
     * Safely adds a column to a table only if it does not already exist, verified via information_schema.
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        if (!columnExists(tableName, columnName)) {
            try {
                log.info("Adding column {} to {}...", columnName, tableName);
                jdbc.execute(String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, columnDefinition));
                log.info("Successfully added column {} to {}.", columnName, tableName);
            } catch (Exception e) {
                log.error("Failed to add column {} to {}: {}", columnName, tableName, e.getMessage());
            }
        } else {
            log.debug("Column {}.{} already exists, skipping addition.", tableName, columnName);
        }
    }

    private void createIndexIfNotExists(String indexName, String tableName, String columns) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC') AND LOWER(TABLE_NAME) = ? AND LOWER(INDEX_NAME) = ?",
                Integer.class, tableName.toLowerCase(), indexName.toLowerCase()
            );
            if (count == null || count == 0) {
                jdbc.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
                log.info("Successfully created index {} on {}({})", indexName, tableName, columns);
            }
        } catch (Exception ex) {
            log.debug("Index {} check or creation note: {}", indexName, ex.getMessage());
        }
    }

    private void createUniqueIndexIfNotExists(String indexName, String tableName, String columns) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC') AND LOWER(TABLE_NAME) = ? AND LOWER(INDEX_NAME) = ?",
                Integer.class, tableName.toLowerCase(), indexName.toLowerCase()
            );
            if (count == null || count == 0) {
                try {
                    jdbc.execute("CREATE UNIQUE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
                    log.info("Successfully created unique index {} on {}({})", indexName, tableName, columns);
                } catch (Exception ex) {
                    log.warn("Could not create unique index {} on {}({}) - possibly due to existing duplicate records. Continuing startup without unique index. Cause: {}",
                            indexName, tableName, columns, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed checking/creating unique index {} on {}: {}. Continuing startup.", indexName, tableName, ex.getMessage());
        }
    }

    private void createLowerEmailUniqueIndexIfNotExists() {
        String indexName = "uq_app_user_email_lower";
        String tableName = "app_user";
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC') AND LOWER(TABLE_NAME) = ? AND LOWER(INDEX_NAME) = ?",
                Integer.class, tableName.toLowerCase(), indexName.toLowerCase()
            );
            if (count == null || count == 0) {
                try {
                    // MySQL 8 functional key part syntax requires double parentheses around expression
                    jdbc.execute("CREATE UNIQUE INDEX " + indexName + " ON " + tableName + " ((LOWER(email)))");
                    log.info("Successfully created unique functional index {} on {}((LOWER(email)))", indexName, tableName);
                } catch (Exception ex) {
                    try {
                        // Fallback attempt without double parentheses if dialect requires
                        jdbc.execute("CREATE UNIQUE INDEX " + indexName + " ON " + tableName + " (LOWER(email))");
                        log.info("Successfully created unique functional index {} on {}(LOWER(email)) via fallback", indexName, tableName);
                    } catch (Exception exFallback) {
                        log.warn("Could not create functional unique index {} on {}((LOWER(email))) (e.g. existing duplicates or unsupported dialect). Continuing startup safely. Cause: {}",
                                indexName, tableName, ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed checking/creating functional unique index {} on {}: {}. Continuing startup safely.", indexName, tableName, ex.getMessage());
        }
    }

    public static LocalDate parseFlexibleDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String clean = text.trim();
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );
        for (DateTimeFormatter dtf : formatters) {
            try {
                return LocalDate.parse(clean, dtf);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        try {
            if (columnExists(tableName, columnName)) {
                log.info("Dropping obsolete column {}.{}", tableName, columnName);
                try {
                    List<String> fkNames = jdbc.queryForList(
                        "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                        "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?) " +
                        "AND (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC')",
                        String.class, tableName, columnName
                    );
                    for (String fk : fkNames) {
                        try {
                            jdbc.execute("ALTER TABLE " + tableName + " DROP FOREIGN KEY " + fk);
                        } catch (Exception ignored) {}
                        try {
                            jdbc.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT " + fk);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception exFk) {
                    log.debug("FK drop check note: {}", exFk.getMessage());
                }
                try {
                    jdbc.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
                    log.info("Successfully dropped column {}.{}", tableName, columnName);
                } catch (Exception exDrop) {
                    log.warn("Could not drop column {}.{}: {}", tableName, columnName, exDrop.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("dropColumnIfExists error for {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    public void migrateInvoiceDateColumn() {
        try {
            // 1. Ensure invoice_date_raw column exists
            addColumnIfNotExists("invoice", "invoice_date_raw", "VARCHAR(255) NULL");

            // 2. Check current data type of invoice_date
            String dataType = null;
            try {
                dataType = jdbc.queryForObject(
                    "SELECT LOWER(DATA_TYPE) FROM information_schema.COLUMNS " +
                    "WHERE LOWER(TABLE_NAME) = 'invoice' AND LOWER(COLUMN_NAME) = 'invoice_date' " +
                    "AND (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = SCHEMA() OR TABLE_SCHEMA = 'PUBLIC') LIMIT 1",
                    String.class
                );
            } catch (Exception ex) {
                log.debug("information_schema query for invoice.invoice_date data type note: {}", ex.getMessage());
            }

            if (dataType != null && (dataType.contains("char") || dataType.contains("text"))) {
                log.info("Migrating invoice.invoice_date from {} to DATE...", dataType);
                // Preserve original text in invoice_date_raw if not already populated
                try {
                    jdbc.execute("UPDATE invoice SET invoice_date_raw = invoice_date WHERE invoice_date_raw IS NULL AND invoice_date IS NOT NULL");
                } catch (Exception exCopy) {
                    log.debug("invoice_date_raw backfill note: {}", exCopy.getMessage());
                }

                // Query all rows to parse dates safely in Java
                try {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, invoice_date, invoice_date_raw FROM invoice");
                    for (Map<String, Object> row : rows) {
                        Long id = ((Number) row.get("id")).longValue();
                        String rawVal = row.get("invoice_date_raw") != null ? row.get("invoice_date_raw").toString()
                                : (row.get("invoice_date") != null ? row.get("invoice_date").toString() : null);

                        LocalDate parsed = parseFlexibleDate(rawVal);
                        if (parsed != null) {
                            jdbc.update("UPDATE invoice SET invoice_date = ? WHERE id = ?", parsed.toString(), id);
                        } else {
                            if (rawVal != null && !rawVal.trim().isEmpty()) {
                                log.warn("UNPARSED_INVOICE_DATE: Invoice id={} has date value '{}' which could not be parsed as dd/MM/yyyy, dd-MM-yyyy, or yyyy-MM-dd. Value preserved in invoice_date_raw.",
                                        id, rawVal);
                            }
                            jdbc.update("UPDATE invoice SET invoice_date = NULL WHERE id = ?", id);
                        }
                    }
                } catch (Exception exParse) {
                    log.warn("Error parsing invoice dates: {}", exParse.getMessage());
                }

                // Alter column to DATE NULL
                try {
                    jdbc.execute("ALTER TABLE invoice MODIFY COLUMN invoice_date DATE NULL");
                    log.info("Successfully converted invoice.invoice_date to DATE NULL (MySQL syntax)");
                } catch (Exception exAlter) {
                    try {
                        jdbc.execute("ALTER TABLE invoice ALTER COLUMN invoice_date DATE NULL");
                        log.info("Successfully converted invoice.invoice_date to DATE NULL (H2/ANSI syntax)");
                    } catch (Exception exH2) {
                        log.warn("Could not alter invoice.invoice_date to DATE: {}. Continuing startup.", exH2.getMessage());
                    }
                }
            } else {
                // If invoice_date is already DATE, check if any unparsed rows in invoice_date_raw can now be populated
                try {
                    List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT id, invoice_date_raw FROM invoice WHERE invoice_date IS NULL AND invoice_date_raw IS NOT NULL"
                    );
                    for (Map<String, Object> row : rows) {
                        Long id = ((Number) row.get("id")).longValue();
                        String rawVal = (String) row.get("invoice_date_raw");
                        LocalDate parsed = parseFlexibleDate(rawVal);
                        if (parsed != null) {
                            jdbc.update("UPDATE invoice SET invoice_date = ? WHERE id = ?", java.sql.Date.valueOf(parsed), id);
                            log.info("Parsed and populated invoice_date for invoice id={} from raw '{}'", id, rawVal);
                        } else if (rawVal != null && !rawVal.trim().isEmpty()) {
                            log.warn("UNPARSED_INVOICE_DATE: Invoice id={} has date value '{}' in invoice_date_raw which could not be parsed.", id, rawVal);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Safe idempotency check note for invoice_date_raw: {}", ex.getMessage());
                }
                log.info("invoice.invoice_date is DATE column. Verification complete.");
            }
        } catch (Exception ex) {
            log.warn("Failed checking/migrating invoice.invoice_date: {}. Continuing startup.", ex.getMessage());
        }
    }

    public void backfillVendorTicketsAndInvoices() {
        try {
            if (!columnExists("vendor_ticket", "invoice_id")) {
                log.warn("Cannot backfill tickets: vendor_ticket.invoice_id column not present.");
                return;
            }

            List<Map<String, Object>> tickets = jdbc.queryForList(
                "SELECT vt.id, vt.ticket_no, vt.invoice_no, vt.owner_id, vt.client_id, vt.vendor_id " +
                "FROM vendor_ticket vt WHERE vt.invoice_id IS NULL"
            );

            if (tickets.isEmpty()) {
                log.info("Invoice <-> VendorTicket sync: All tickets already have linked invoice records.");
                return;
            }

            int matchedCount = 0;
            int unmatchedCount = 0;

            for (Map<String, Object> t : tickets) {
                Long ticketId = ((Number) t.get("id")).longValue();
                String ticketNo = (String) t.get("ticket_no");
                String invoiceNo = (String) t.get("invoice_no");
                Long ownerId = t.get("owner_id") != null ? ((Number) t.get("owner_id")).longValue() : null;
                Long clientId = t.get("client_id") != null ? ((Number) t.get("client_id")).longValue() : null;
                Long vendorId = t.get("vendor_id") != null ? ((Number) t.get("vendor_id")).longValue() : null;

                if (invoiceNo == null || invoiceNo.isBlank()) {
                    log.warn("UNSYNCED_TICKET: Ticket {} (id={}) has no invoice number specified.", ticketNo, ticketId);
                    unmatchedCount++;
                    continue;
                }

                Long matchedInvoiceId = null;
                try {
                    if (clientId != null) {
                        List<Long> ids = jdbc.queryForList(
                            "SELECT id FROM invoice WHERE LOWER(TRIM(invoice_number)) = LOWER(TRIM(?)) AND client_id = ? ORDER BY id DESC LIMIT 1",
                            Long.class, invoiceNo, clientId
                        );
                        if (!ids.isEmpty()) {
                            matchedInvoiceId = ids.get(0);
                        }
                    }
                    if (matchedInvoiceId == null && (ownerId != null || vendorId != null)) {
                        Long effOwner = ownerId != null ? ownerId : vendorId;
                        List<Long> ids = jdbc.queryForList(
                            "SELECT i.id FROM invoice i JOIN client c ON i.client_id = c.id WHERE LOWER(TRIM(i.invoice_number)) = LOWER(TRIM(?)) AND c.owner_id = ? ORDER BY i.id DESC LIMIT 1",
                            Long.class, invoiceNo, effOwner
                        );
                        if (!ids.isEmpty()) {
                            matchedInvoiceId = ids.get(0);
                        }
                    }
                } catch (Exception exQuery) {
                    log.debug("Query error while matching ticket {}: {}", ticketNo, exQuery.getMessage());
                }

                if (matchedInvoiceId != null) {
                    try {
                        jdbc.update("UPDATE vendor_ticket SET invoice_id = ? WHERE id = ?", matchedInvoiceId, ticketId);
                        matchedCount++;
                        log.info("Backfilled sync: Ticket {} (id={}) successfully linked to Invoice id={}", ticketNo, ticketId, matchedInvoiceId);
                    } catch (Exception exUpdate) {
                        log.warn("Failed to update link for ticket {}: {}", ticketNo, exUpdate.getMessage());
                        unmatchedCount++;
                    }
                } else {
                    unmatchedCount++;
                    log.warn("UNSYNCED_TICKET: Ticket {} (id={}, invoiceNo='{}', clientId={}) could not be matched to an existing Invoice record.",
                            ticketNo, ticketId, invoiceNo, clientId);
                }
            }

            log.info("Invoice <-> VendorTicket sync finished: {} tickets matched & linked, {} unmatched/unsynced tickets.",
                    matchedCount, unmatchedCount);
        } catch (Exception ex) {
            log.warn("Error during backfillVendorTicketsAndInvoices: {}. Continuing startup safely.", ex.getMessage());
        }
    }
}
