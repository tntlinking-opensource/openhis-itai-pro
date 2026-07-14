/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.wi.starter;

import com.openhis.wi.application.config.WiJilinApplicationAutoConfiguration;
import com.openhis.wi.bridge.config.WiBridgeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/** 工伤 Module HIS 侧装配入口（Spring Boot AutoConfiguration）。
 *
 * <p>引入 {@link WiBridgeAutoConfiguration} 与 {@link WiJilinApplicationAutoConfiguration}，
 * 由 {@code openhis.modules.work-injury.enabled} 控制是否加载。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@Import({WiBridgeAutoConfiguration.class, WiJilinApplicationAutoConfiguration.class})
public class WiJilinModuleAutoConfiguration {
}
