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
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 按顺序执行多套 Flyway（各模块独立 history 表），对外仍暴露为 bean 名 {@code flywayInitializer}。
 * <p>须继承 {@link FlywayMigrationInitializer}，以便在 {@code spring.flyway.enabled=true} 时抑制 Boot 默认同名 Bean。</p>
 */
public class ChainedFlywayMigrationInitializer extends FlywayMigrationInitializer {

    public ChainedFlywayMigrationInitializer(List<Flyway> flyways) {
        super(requireFirst(flyways), flyway -> {
            for (Flyway chain : flyways) {
                chain.migrate();
            }
        });
    }

    private static Flyway requireFirst(List<Flyway> flyways) {
        Assert.notEmpty(flyways, "flyway chain must not be empty");
        return flyways.get(0);
    }
}
