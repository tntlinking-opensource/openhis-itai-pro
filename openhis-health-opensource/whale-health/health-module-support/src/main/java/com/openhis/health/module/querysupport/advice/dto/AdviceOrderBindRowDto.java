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

package com.openhis.health.module.querysupport.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-DOR-12：医嘱绑定明细行（用法→耗材 或 诊疗套餐子项）。
 *
 * <p>由 {@link com.openhis.health.module.querysupport.advice.AdviceOrderBindReadLoader}
 * 从全院模板装配；条数受 {@link com.openhis.health.domain.share.constants.DoctorStationAdviceConstants#ORDER_BIND_LIST_MAX_ROWS} 限制。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱绑定明细行")
public class AdviceOrderBindRowDto {

    /** 绑定类型回显。 */
    @Schema(description = "绑定类型回显")
    private String bindType;

    /** 诊疗耗材绑定模板主键（{@code cus_service_device_template.id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定模板主键")
    private Long bindingId;

    /** 源定义主键；用法绑定场景为空（来源键为 {@code method_code}），诊疗绑定为活动定义主键。 */
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

    /** 目标业务编码。 */
    @Schema(description = "目标业务编码")
    private String busNo;

    /** 目标规格展示。 */
    @Schema(description = "目标规格展示")
    private String specificationText;

    /** 零售单价（每包装单位售价）。 */
    @Schema(description = "零售单价")
    private BigDecimal unitPrice;

    /** 收费项定义主键（与 {@link #unitPrice} 对应）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定义主键")
    private Long chargeItemDefinitionId;

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
