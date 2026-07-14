/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** whale-web 层 Bean（OpenAPI、Jackson 等）。其余 framework 子模块各自 AutoConfiguration。 */
@AutoConfiguration
@ComponentScan(basePackages = "com.openhis.whale.web")
public class WhaleFrameworkAutoConfiguration {
}
