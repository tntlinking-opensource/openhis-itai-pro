package com.openhis.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 入口。
 * <p>扫描 {@code com.openhis.health}（HIS 核心，含内嵌 MRMS/LIS 等子域）；EMR/Report/CHS 等扩展模块
 * 经各自 {@code AutoConfiguration} 自装配。</p>
 */
@SpringBootApplication(scanBasePackages = "com.openhis.health")
@EntityScan(basePackages = "com.openhis.health")
@EnableJpaRepositories(basePackages = "com.openhis.health")
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class HealthApplication {

    /**
     * Spring Boot 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
    }
}
