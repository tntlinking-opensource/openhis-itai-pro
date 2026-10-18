/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.starter;

import com.zxplat.plugin.wi.application.config.WiJilinApplicationAutoConfiguration;
import com.zxplat.plugin.wi.bridge.config.WiBridgeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/** 工伤 Module HIS 侧装配入口（Spring Boot AutoConfiguration）。
 *
 * <p>引入 {@link WiBridgeAutoConfiguration} 与 {@link WiJilinApplicationAutoConfiguration}，
 * 由 {@code zxplat.modules.work-injury.enabled} 控制是否加载。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
@Import({WiBridgeAutoConfiguration.class, WiJilinApplicationAutoConfiguration.class})
public class WiJilinModuleAutoConfiguration {
}
