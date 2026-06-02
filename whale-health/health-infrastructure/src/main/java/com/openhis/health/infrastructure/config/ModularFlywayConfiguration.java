/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.infrastructure.config;

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
 * 与 {@code health-infrastructure} 内 {@code db/migration/*} 脚本同模块维护；院点与 {@code health-app} 通过依赖本模块装配。
 */
@Configuration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(name = "openhis.flyway.modular.enabled", havingValue = "true", matchIfMissing = true)
public class ModularFlywayConfiguration {

    private static final String HISTORY_CORE = "flyway_schema_history";
    private static final String HISTORY_MRMS = "flyway_schema_history_mrms";
    private static final String HISTORY_CHS = "flyway_schema_history_chs";

    @Bean(name = "flywayInitializer")
    @ConditionalOnMissingBean(FlywayMigrationInitializer.class)
    public ChainedFlywayMigrationInitializer flywayInitializer(DataSource dataSource, Environment env) {
        List<Flyway> chain = new ArrayList<>();
        chain.add(coreFlyway(dataSource, env));
        if (isModuleEnabled(env, "openhis.modules.mrms.enabled")) {
            chain.add(moduleFlyway(dataSource, env, "classpath:db/migration/mrms", HISTORY_MRMS));
        }
        if (isModuleEnabled(env, "openhis.modules.chs.enabled")) {
            chain.add(moduleFlyway(dataSource, env, "classpath:db/migration/chs", HISTORY_CHS));
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
