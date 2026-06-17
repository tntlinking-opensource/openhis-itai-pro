/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
/** 医生站医嘱目录预选产品键。 */
@Schema(description = "医生站医嘱目录预选产品键")
public class PrepickedProductKeyDto {

    /** 目录复合键之一：产品定义主键，与 IF-IP-DOR-11 目录行锚点对齐。 */
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
