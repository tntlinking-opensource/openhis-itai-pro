/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.settings.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * PEIS 模块参数 DTO（CFG-13）。
 */
@Data
public class PeisConfigDto {

    /** 配置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置键。 */
    private String configKey;

    /** 配置值。 */
    private String configValue;

    /** 院区机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
}
