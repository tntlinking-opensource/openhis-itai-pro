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
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-DOR-12：医嘱绑定明细行（用法→耗材 或 诊疗套餐子项）。
 *
 * <p>由 {@link com.openhis.health.application.inpatient.doctorstation.advice.query.InpatientDoctorStationAdviceOrderBindQueryService}
 * 从全院模板装配；条数受 {@link com.openhis.health.domain.share.constants.DoctorStationAdviceConstants#ORDER_BIND_LIST_MAX_ROWS} 限制。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱绑定明细行")
public class InpatientDoctorStationAdviceOrderBindRowDto {

    /** 绑定类型回显。 */
    @Schema(description = "绑定类型回显")
    private String bindType;

    /** 源定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源定义主键")
    private Long sourceItemId;

    /** 源展示名。 */
    @Schema(description = "源展示名")
    private String sourceItemName;

    /** 目标定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标定义主键")
    private Long targetItemId;

    /** 目标定义物理表（{@code wkf_activity_definition} / {@code adm_device_definition}）。 */
    @Schema(description = "目标定义物理表")
    private String targetItemTable;

    /** 目标展示名。 */
    @Schema(description = "目标展示名")
    private String targetItemName;

    /** 数量分子。 */
    @Schema(description = "数量分子")
    private BigDecimal quantityNumerator;

    /** 数量分母。 */
    @Schema(description = "数量分母")
    private BigDecimal quantityDenominator;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 与 {@link #unitCode} 对应的单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer priority;

    /** 是否有效。 */
    @Schema(description = "是否有效")
    private Boolean activeFlag;
}
