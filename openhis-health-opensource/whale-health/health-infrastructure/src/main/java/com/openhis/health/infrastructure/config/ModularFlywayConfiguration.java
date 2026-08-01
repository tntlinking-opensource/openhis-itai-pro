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
import java.util.Comparator;
import java.util.List;

/**
 * 多模块 Flyway：同一数据库、各自独立的 {@code flyway_schema_history_*} 表。
 * <p>
 * health 核心脚本在 {@code db/migration/health}（V1–V10，增量接着编如 V26、V27）；
 * 扩展 Module 通过 {@code META-INF/openhis/flyway-module.json} 声明 locations 与 history 表。
 * <p>
 * 链顺序：health 核心 → 业务 Module → 院点 {@code project-*} → health-dev（可选）→ health-final。
 * <p>
 * health-dev 为 V11–V25 且独立 history；health-final 的 V99 与核心共用 history。
 * 核心 {@code outOfOrder=true}，这样晚加的 V26+ 在已落过 V99 的库上仍会执行。
 */
@Configuration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(name = "openhis.flyway.modular.enabled", havingValue = "true", matchIfMissing = true)
public class ModularFlywayConfiguration {

    private static final String HISTORY_CORE = "flyway_schema_history";
    private static final String HISTORY_DEV = "flyway_schema_history_dev";

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
        // 1. health 核心 DDL / platform seed（V1–V10，增量接着编）
        chain.add(coreFlyway(dataSource, env));
        // 2. 扩展 Module；院点 project-* 置后
        List<OpenHisFlywayModuleDefinition> modules = new ArrayList<>(OpenHisFlywayModuleCatalog.entries());
        modules.sort(Comparator
            .comparing((OpenHisFlywayModuleDefinition m) -> isProjectModule(m) ? 1 : 0)
            .thenComparing(m -> m.moduleId() == null ? "" : m.moduleId()));
        for (OpenHisFlywayModuleDefinition module : modules) {
            if (env.getProperty(module.enabledProperty(), Boolean.class, false)) {
                chain.add(moduleFlyway(dataSource, env, module.locations(), module.historyTable()));
            }
        }
        // 3. health-dev 演示种子（V11–V25，独立 history）
        if (Boolean.TRUE.equals(env.getProperty("openhis.flyway.dev-seed.enabled", Boolean.class, false))) {
            String devLocation = env.getProperty("openhis.flyway.dev-seed.locations", "classpath:db/migration/health-dev");
            chain.add(moduleFlyway(dataSource, env, devLocation, HISTORY_DEV));
        }
        // 4. health-final（V99 whale_sequence，与核心共用 history）
        //    仅含 V99；validate 时须 ignore *:missing，否则会把已执行的 V1–Vn 误判为 missing
        chain.add(healthFinalFlyway(dataSource, env));
        return new ChainedFlywayMigrationInitializer(chain);
    }

    /** 院点 project 的 Flyway 模块（{@code moduleId} 以 {@code project-} 开头）。 */
    private static boolean isProjectModule(OpenHisFlywayModuleDefinition module) {
        String id = module.moduleId();
        return id != null && id.startsWith("project-");
    }

    /** health 核心：classpath:db/migration/health。 */
    private static Flyway coreFlyway(DataSource dataSource, Environment env) {
        return sameHistoryFlyway(dataSource, env, "classpath:db/migration/health");
    }

    /** health 核心：与 HISTORY_CORE 绑定。 */
    private static Flyway sameHistoryFlyway(DataSource dataSource, Environment env, String location) {
        return baseConfigurer(dataSource, env)
                .locations(location)
                .table(HISTORY_CORE)
                .load();
    }

    /**
     * health-final：仅 {@code V99}，与核心共用 {@link #HISTORY_CORE}。
     * <p>必须 {@code ignoreMigrationPatterns("*:missing")}（Flyway 9+ 替代已移除的
     * {@code ignoreMissingMigrations}）：否则 prod {@code validate-on-migrate=true} 时，
     * 会因本 location 解析不到已执行的 V1–Vn 而 Validate failed。</p>
     */
    private static Flyway healthFinalFlyway(DataSource dataSource, Environment env) {
        return baseConfigurer(dataSource, env)
                .locations("classpath:db/migration/health-final")
                .table(HISTORY_CORE)
                .ignoreMigrationPatterns("*:missing")
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
                // 核心与 health-final 共用 history；V99 已落地后仍允许补跑晚加的 V26+ 增量
                .outOfOrder(true)
                .cleanDisabled(true);
    }
}
