package com.openhis.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot 入口。
 * <p>扫描 {@code com.openhis} 全树；Whale Framework 各子模块另通过 {@code META-INF/spring/...AutoConfiguration.imports} 自装配。</p>
 */
@SpringBootApplication(scanBasePackages = "com.openhis")
@EntityScan(basePackages = "com.openhis")
@EnableJpaRepositories(basePackages = "com.openhis")
@EnableJpaAuditing
@EnableCaching
@EnableAsync
public class HealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
    }
}
