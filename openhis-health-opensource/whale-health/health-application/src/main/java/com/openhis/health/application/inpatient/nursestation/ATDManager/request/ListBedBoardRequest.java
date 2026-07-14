/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-01：床位列表查询入参（与 001 左侧树范围一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位列表查询请求", requiredProperties = {"panel"})
public class ListBedBoardRequest {

    /** 病区位置主键。 */
    @Schema(description = "病区位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病房位置主键；优先于 {@code wardId} 收窄范围。 */
    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 床列表面板：{@code admission} 入科 / {@code discharge} 出。 */
    @NotBlank(message = "床列表面板不能为空")
    @Schema(description = "床列表面板：admission 入科 / discharge 出", requiredMode = Schema.RequiredMode.REQUIRED)
    private String panel;
}
