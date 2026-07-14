/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-11：预选目录项（product_definition_id + product_table_name）。
 *
 * <p>组套套用、历史行 hydration 等快捷入口以 ids 收窄目录分页；
 * 价与 {@code defaultDispenseLocationId} 由目录读 IF 返回（住院写路径不使用）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱目录预选产品键")
public class PrepickedProductKeyDto {

    /** 产品定义主键。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_DEFINITION_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productDefinitionId;

    /** 目录复合键之一：定义层物理表名（仅药品/诊疗两表白名单）。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    /** 产品来源表名。 */
    @Schema(description = "产品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
