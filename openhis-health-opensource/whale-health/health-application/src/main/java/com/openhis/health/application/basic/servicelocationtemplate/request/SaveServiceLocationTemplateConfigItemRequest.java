/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicelocationtemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * 整科覆盖保存中的单条取药规则行。
 */
@Data
@Schema(description = "取药规则配置行", requiredProperties = {
    "itemTable", "distributionCategoryCode", "defLocationId", "priority"
})
public class SaveServiceLocationTemplateConfigItemRequest {

    /** 物品表名。 */
    @NotBlank(message = "物品表名不能为空")
    @Schema(description = "物品表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 发放类别编码（须与目录 categoryCode 一致）。 */
    @NotBlank(message = "发放类别不能为空")
    /** 发放类别编码。 */
    @Schema(description = "发放类别编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String distributionCategoryCode;

    /** 默认发药库位标识。 */
    @NotNull(message = "默认发药位置不能为空")
    /** 默认发药库位标识。 */
    @Schema(description = "默认发药库位标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defLocationId;

    /** 具体物品定义标识。 */
    @Schema(description = "具体物品定义标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemDefinitionId;

    /**
     * 耗材种类编码（术语 {@code device_type_code}）；空为通配。
     * 建议仅耗材行使用；药品行应为空。
     */
    @Schema(description = "耗材种类编码")
    private String typeCode;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 生效开始时间。 */
    @Schema(description = "生效开始时间")
    private LocalTime startTime;

    /** 生效结束时间。 */
    @Schema(description = "生效结束时间")
    private LocalTime endTime;

    /** 优先级，从 1 起连续，数值越小越优先。 */
    @NotNull(message = "优先级不能为空")
    /** 优先级。 */
    @Schema(description = "优先级", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer priority;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
