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

    private static final Logger log = LoggerFactory.getLogger("whale.startup");

    public ChainedFlywayMigrationInitializer(List<Flyway> flyways) {
        super(requireFirst(flyways), flyway -> migrateAll(flyways));
    }

    private static void migrateAll(List<Flyway> flyways) {
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

    private static Flyway requireFirst(List<Flyway> flyways) {
        Assert.notEmpty(flyways, "flyway chain must not be empty");
        return flyways.get(0);
    }

    private static String formatLocations(Location[] locations) {
        if (locations == null || locations.length == 0) {
            return "-";
        }
        return Arrays.stream(locations)
            .map(Location::getDescriptor)
            .collect(Collectors.joining(","));
    }
}
