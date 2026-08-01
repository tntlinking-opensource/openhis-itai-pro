/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 清床前软提示：已核发但未分配追溯码的发药行摘要。
 */
@Data
@Accessors(chain = true)
@Schema(description = "缺追溯码发药提示行")
public class MissingDispenseTraceTipDto {

    /** 发放行主键。 */
    @Schema(description = "发放行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 发药归属日（yyyy-MM-dd），优先发药时刻。 */
    @Schema(description = "发药归属日 yyyy-MM-dd")
    private String dispenseDate;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 批号；可空。 */
    @Schema(description = "批号")
    private String lotNumber;

    /**
     * 前端可直接展示的提示文案，例如「2026-07-18 阿莫西林胶囊（批号 A01）未分配追溯码」。
     */
    @Schema(description = "提示文案")
    private String tipText;
}
