package com.openhis.chs.application.deployment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CHS 独立进程入口（remote 部署）。
 */
@SpringBootApplication(scanBasePackages = "com.openhis.chs")
public class ChsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChsServerApplication.class, args);
    }
}
