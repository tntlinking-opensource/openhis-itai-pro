/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.legacyfile;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 {@link PeisLegacyFileProperties}；读接口 {@link PeisLegacyFileController} 在 enabled=true 时生效。
 */
@Configuration
@EnableConfigurationProperties(PeisLegacyFileProperties.class)
public class PeisLegacyFileConfiguration {
}
