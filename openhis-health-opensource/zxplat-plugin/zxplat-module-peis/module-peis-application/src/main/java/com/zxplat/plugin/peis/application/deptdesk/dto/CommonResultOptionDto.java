/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 常见结果下拉项 DTO（DES-03）。
 */
@Data
public class CommonResultOptionDto {

    /** 常见结果主键。 */
    @Schema(description = "常见结果主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称。 */
    @Schema(description = "模板名称")
    private String display;

    /** 默认结果值。 */
    @Schema(description = "默认结果值")
    private String valueString;

    /** 解读编码。 */
    @Schema(description = "解读编码")
    private String interpretationCode;
}
