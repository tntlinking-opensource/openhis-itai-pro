/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** ELEP 模块自装配：扫描 application + domain + api。 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.elep.application",
        "com.openhis.elep.domain",
        "com.openhis.elep.api"
})
@EntityScan(basePackages = "com.openhis.elep.domain")
@EnableJpaRepositories(basePackages = "com.openhis.elep.domain")
public class ElepAutoConfiguration {
}
