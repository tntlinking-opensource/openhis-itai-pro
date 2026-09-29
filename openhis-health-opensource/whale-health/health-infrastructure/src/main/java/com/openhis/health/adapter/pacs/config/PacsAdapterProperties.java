/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PACS adapter 开关与 Mock 行为（{@code openhis.modules.pacs.adapter.*}）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openhis.modules.pacs.adapter")
public class PacsAdapterProperties {

    /** 是否启用 adapter 装配（默认 false）。 */
    private boolean enabled = false;

    /** Mock 外发是否默认成功（默认 true）。 */
    private boolean mockOutboundSuccess = true;
}
