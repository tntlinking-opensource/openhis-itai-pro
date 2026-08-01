/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.peisconfig.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存 PEIS 模块参数命令。
 */
@Getter
@Setter
public class SavePeisConfigCommand {

    /** 更新时主键。 */
    private Long id;

    /** 租户。 */
    private Long tenantId;

    /** 院区。 */
    private Long orgId;

    /** 配置键。 */
    private String configKey;

    /** 配置值。 */
    private String configValue;
}
