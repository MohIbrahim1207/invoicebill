package com.billing.invoicehub.service;

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

            // Ensure app_roles table exists
            jdbc.execute("CREATE TABLE IF NOT EXISTS app_roles (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, UNIQUE KEY uq_app_roles_name (name))");

            // Ensure core roles are present
            try {
                jdbc.update("INSERT IGNORE INTO app_roles (name) VALUES (?)", "ROLE_ADMIN");
                jdbc.update("INSERT IGNORE INTO app_roles (name) VALUES (?)", "ROLE_USER");
            } catch (Exception ex) {
                // Some drivers don't support INSERT IGNORE; fall back to existence-check insert
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ?", Integer.class, "ROLE_ADMIN");
                if (count == 0) jdbc.update("INSERT INTO app_roles (name) VALUES (?)", "ROLE_ADMIN");
                count = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ?", Integer.class, "ROLE_USER");
                if (count == 0) jdbc.update("INSERT INTO app_roles (name) VALUES (?)", "ROLE_USER");
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

            log.info("Startup role migration completed.");
        } catch (Exception ex) {
            log.warn("Startup role migration failed - continuing startup. Cause: {}", ex.getMessage());
        }
    }
}


