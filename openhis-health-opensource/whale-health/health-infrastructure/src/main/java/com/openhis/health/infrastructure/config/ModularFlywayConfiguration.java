/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.infrastructure.config;

import com.openhis.whale.core.module.OpenHisFlywayModuleCatalog;
import com.openhis.whale.core.module.OpenHisFlywayModuleDefinition;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 多模块 Flyway：同一数据库、各自独立的 {@code flyway_schema_history_*} 表。
 * <p>
 * health 核心脚本在 {@code db/migration/health}；扩展 Module 通过
 * {@code META-INF/openhis/flyway-module.json} 声明 locations 与 history 表。
 */
@Configuration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(name = "openhis.flyway.modular.enabled", havingValue = "true", matchIfMissing = true)
public class ModularFlywayConfiguration {

    private static final String HISTORY_CORE = "flyway_schema_history";

    @Bean(name = "flywayInitializer")
    @ConditionalOnMissingBean(FlywayMigrationInitializer.class)
    public ChainedFlywayMigrationInitializer flywayInitializer(DataSource dataSource, Environment env) {
        List<Flyway> chain = new ArrayList<>();
        chain.add(coreFlyway(dataSource, env));
        for (OpenHisFlywayModuleDefinition module : OpenHisFlywayModuleCatalog.entries()) {
            if (isModuleEnabled(env, module.enabledProperty())) {
                chain.add(moduleFlyway(dataSource, env, module.locations(), module.historyTable()));
            }
        }
        return new ChainedFlywayMigrationInitializer(chain);
    }

    private static Flyway coreFlyway(DataSource dataSource, Environment env) {
        return baseConfigurer(dataSource, env)
                .locations("classpath:db/migration/health")
                .table(HISTORY_CORE)
                .load();
    }

    private static Flyway moduleFlyway(
            DataSource dataSource, Environment env, String location, String historyTable) {
        return baseConfigurer(dataSource, env)
                .locations(location)
                .table(historyTable)
                .load();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration baseConfigurer(
            DataSource dataSource, Environment env) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(env.getProperty("spring.flyway.validate-on-migrate", Boolean.class, false))
                .outOfOrder(false)
                .cleanDisabled(true);
    }

    private static boolean isModuleEnabled(Environment env, String key) {
        return env.getProperty(key, Boolean.class, false);
    }
}
