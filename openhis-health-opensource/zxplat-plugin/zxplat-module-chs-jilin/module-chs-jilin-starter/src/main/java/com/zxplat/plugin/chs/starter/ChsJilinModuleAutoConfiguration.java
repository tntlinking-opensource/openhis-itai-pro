/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.starter;

import com.zxplat.plugin.chs.application.config.ChsJilinApplicationAutoConfiguration;
import com.zxplat.plugin.chs.bridge.config.ChsBridgeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/** CHS Module HIS 侧装配入口。 */
@AutoConfiguration
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
@Import({ChsBridgeAutoConfiguration.class, ChsJilinApplicationAutoConfiguration.class})
public class ChsJilinModuleAutoConfiguration {
}
