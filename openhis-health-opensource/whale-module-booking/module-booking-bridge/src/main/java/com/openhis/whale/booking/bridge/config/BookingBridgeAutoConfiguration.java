/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * booking-bridge 自动配置：扫描 bridge 编排/读写下与 health Integration SPI 实现。
 *
 * <p>须显式扫描 {@code com.openhis.health.integration.booking}，否则
 * {@code OutpatientBookingRegistrationExtensionImpl} 等 {@code @Component} 无法注册，
 * health 侧会落到 NoOp（{@code isEnabled=false}），现场挂号不占号、余量永不减。</p>
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.whale.booking.bridge.outpatient.workflow.BookingOutpatientQueryOrchestrator")
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.booking.application.config.BookingAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.booking.bridge",
        "com.openhis.health.integration.booking",
        "com.openhis.health.infrastructure.integration.booking"
})
public class BookingBridgeAutoConfiguration {
}
