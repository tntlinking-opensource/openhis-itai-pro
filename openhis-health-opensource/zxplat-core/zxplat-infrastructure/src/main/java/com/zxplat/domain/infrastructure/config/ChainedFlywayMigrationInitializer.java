/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按顺序执行多套 Flyway（各模块独立 history 表），对外仍暴露为 bean 名 {@code flywayInitializer}。
 * <p>须继承 {@link FlywayMigrationInitializer}，以便在 {@code spring.flyway.enabled=true} 时抑制 Boot 默认同名 Bean。</p>
 */
public class ChainedFlywayMigrationInitializer extends FlywayMigrationInitializer {

    private static final Logger log = LoggerFactory.getLogger("zxplat.startup");

    /**
     * 构造链式 Flyway 初始化器，委托父类在 Spring 容器启动时触发 {@link #migrateAll}。
     *
     * @param flyways 按迁移顺序排列的 Flyway 实例，至少一项
     */
    public ChainedFlywayMigrationInitializer(List<Flyway> flyways) {
        super(requireFirstFlyway(flyways), ignored -> migrateAll(flyways));
    }

    /**
     * 校验链非空并返回首项，供 {@code super(...)} 作为构造首语句调用。
     *
     * @param flyways 按迁移顺序排列的 Flyway 实例
     * @return 链首 Flyway
     */
    private static Flyway requireFirstFlyway(List<Flyway> flyways) {
        Assert.notEmpty(flyways, "flyway chain must not be empty");
        return flyways.get(0);
    }

    /**
     * 按注册顺序逐套执行 Flyway 迁移（各模块独立 history 表）。
     *
     * @param flyways 链式 Flyway 实例列表
     */
    private static void migrateAll(List<Flyway> flyways) {
        // 按注册顺序逐套迁移，各 Module 独立 history 表互不影响
        for (Flyway chain : flyways) {
            String historyTable = chain.getConfiguration().getTable();
            String locations = formatLocations(chain.getConfiguration().getLocations());
            log.info(">>> Flyway 迁移开始: historyTable={}, locations={}", historyTable, locations);
            MigrateResult result = chain.migrate();
            log.info(
                ">>> Flyway 迁移完成: historyTable={}, executed={}, success={}",
                historyTable,
                result.migrationsExecuted,
                result.success);
        }
    }

    /**
     * 将 Flyway 配置 locations 格式化为日志可读串。
     *
     * @param locations 配置位置数组
     * @return 逗号分隔描述符；空则 {@code "-"}
     */
    private static String formatLocations(Location[] locations) {
        if (locations == null || locations.length == 0) {
            return "-";
        }
        return Arrays.stream(locations)
            .map(Location::getDescriptor)
            .collect(Collectors.joining(","));
    }
}
