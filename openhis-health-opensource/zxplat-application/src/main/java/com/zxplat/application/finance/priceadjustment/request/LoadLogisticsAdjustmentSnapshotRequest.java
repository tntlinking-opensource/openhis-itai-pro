/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 物流批号快照加载请求（IF-PRC-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "物流批号快照加载请求", requiredProperties = {"itemTable", "itemIds"})
public class LoadLogisticsAdjustmentSnapshotRequest {

    /** 项目物理表。 */
    @NotBlank(message = "项目表不能为空")
    @Schema(description = "项目物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 项目主键列表。 */
    @NotEmpty(message = "项目列表不能为空")
    @Schema(description = "项目主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> itemIds;
}
