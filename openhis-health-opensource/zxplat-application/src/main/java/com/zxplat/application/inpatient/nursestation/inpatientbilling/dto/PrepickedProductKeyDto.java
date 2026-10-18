/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-PRC-01：预选目录项（product_definition_id + product_table_name）。
 *
 * <p>组套 IF-08 预览、历史行 hydration 等快捷入口以 ids 收窄目录分页，与 IF-DOR-11
 * {@code prepickedProductKeys} 同套路；读侧返回 {@code defaultDispenseLocationId}，写 IF-02/04 回传
 * {@code performLocation}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士划价选品目录预选产品键")
public class PrepickedProductKeyDto {

    /** 产品定义主键。 */
    @NotNull(message = "产品定义主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productDefinitionId;

    /** 产品来源表名。 */
    @NotBlank(message = "产品来源表名不能为空")
    /** 产品来源表名。 */
    @Schema(description = "产品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
