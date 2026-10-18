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

package openhis.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OpenHIS 产品演示院点（SiteId=openhis）应用启动类。
 *
 * <p>仅加载主线包；院点定制请在 {@code openhis} 包下按业务域追加 L2 Bean。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "openhis",
        "com.zxplat.framework",
        "com.zxplat.domain",
        "com.zxplat.application",
        "com.zxplat.starter.config"
})
@EntityScan(basePackages = "com.zxplat.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.domain")
@EnableJpaAuditing
@EnableCaching
@EnableAsync
public class OpenHisApplication {

    /**
     * Spring Boot 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OpenHisApplication.class, args);
    }
}
