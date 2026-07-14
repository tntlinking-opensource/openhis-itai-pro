/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /**
     * 注册链式 Flyway 初始化器：先迁移 health 核心脚本，再按开关依次迁移扩展 Module。
     *
     * @param dataSource 数据源
     * @param env Spring 环境，读取各模块 enabled 与 validate-on-migrate
     * @return 按顺序执行多套 history 表的迁移初始化器
     */
    @Bean(name = "flywayInitializer")
    @ConditionalOnMissingBean(FlywayMigrationInitializer.class)
    public ChainedFlywayMigrationInitializer flywayInitializer(DataSource dataSource, Environment env) {
        List<Flyway> chain = new ArrayList<>();
        // 1. health DDL + platform seed (V1–V10)
        chain.add(coreFlyway(dataSource, env));
        // 2. 扩展 Module DDL / platform seed（含 booking bkg_* 等）
        for (OpenHisFlywayModuleDefinition module : OpenHisFlywayModuleCatalog.entries()) {
            if (env.getProperty(module.enabledProperty(), Boolean.class, false)) {
                chain.add(moduleFlyway(dataSource, env, module.locations(), module.historyTable()));
            }
        }
        // 3. health-dev 演示种子（V11–V18，依赖模块表）
        if (Boolean.TRUE.equals(env.getProperty("openhis.flyway.dev-seed.enabled", Boolean.class, false))) {
            String devLocation = env.getProperty("openhis.flyway.dev-seed.locations", "classpath:db/migration/health-dev");
            chain.add(sameHistoryFlyway(dataSource, env, devLocation));
        }
        // 4. whale_sequence 同步（V99，须在 platform + dev seed 之后）
        chain.add(sameHistoryFlyway(dataSource, env, "classpath:db/migration/health-final"));
        return new ChainedFlywayMigrationInitializer(chain);
    }

    /** health 核心：仅 classpath:db/migration/health（V1–V10）。 */
    private static Flyway coreFlyway(DataSource dataSource, Environment env) {
        return sameHistoryFlyway(dataSource, env, "classpath:db/migration/health");
    }

    /** 与 health 共用 flyway_schema_history 的后续阶段（dev seed / V99）。 */
    private static Flyway sameHistoryFlyway(DataSource dataSource, Environment env, String location) {
        return baseConfigurer(dataSource, env)
                .locations(location)
                .table(HISTORY_CORE)
                .load();
    }

    /**
     * 构建扩展模块 Flyway 实例（独立 locations 与 history 表）。
     *
     * @param dataSource   数据源
     * @param env          Spring 环境
     * @param location     脚本 classpath 位置
     * @param historyTable Flyway history 表名
     * @return 已 load 的 Flyway
     */
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
}
