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

package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-23：暂存前绑定解析明细行（含触发源与 based_on 锚点）。
 */
@Data
@Accessors(chain = true)
/** 医生站暂存前绑定解析明细行。 */
@Schema(description = "医生站暂存前绑定解析明细行")
public class DoctorStationAdviceDraftBindItemDto {

    /** 绑定来源类型：{@code USAGE} / {@code ACTIVITY}。 */
    @Schema(description = "绑定来源类型：USAGE / ACTIVITY")
    private String bindType;

    /** 源定义主键；用法绑定场景为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源定义主键；用法绑定为空")
    private Long sourceItemId;

    /** 源展示名（用法术语名或诊疗名）。 */
    @Schema(description = "源展示名")
    private String sourceItemName;

    /** 绑定目标定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定目标定义主键")
    private Long targetItemId;

    /** 绑定目标展示名。 */
    @Schema(description = "绑定目标展示名")
    private String targetItemName;

    /** 绑定目标物理表：{@code wkf_activity_definition} / {@code adm_device_definition}。 */
    @Schema(description = "绑定目标产品定义表名")
    private String targetProductTableName;

    /** 模板默认数量分子（不乘主医嘱数量）。 */
    @Schema(description = "数量分子")
    private BigDecimal quantityNumerator;

    /** 数量分母，固定为 1。 */
    @Schema(description = "数量分母")
    private BigDecimal quantityDenominator;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 与 {@link #unitCode} 对应的单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 模板内展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer priority;

    /** 模板是否启用。 */
    @Schema(description = "是否有效")
    private Boolean activeFlag;

    /** 触发绑定的暂存变更行下标（与 Request.lines 同序，从 0 起）。 */
    @Schema(description = "触发暂存变更行下标")
    private Integer triggerLineIndex;

    /** 触发行的 lineOp（CREATE / UPDATE）。 */
    @Schema(description = "触发行操作码")
    private String triggerLineOp;

    /** 触发主医嘱产品定义表名。 */
    @Schema(description = "触发主医嘱产品定义表名")
    private String triggerProductTableName;

    /** 触发主医嘱定义主键（药品或诊疗活动定义）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "触发主医嘱定义主键")
    private Long triggerDefinitionId;

    /** 触发主医嘱请求主键；CREATE 行暂存前可为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "触发主医嘱请求主键")
    private Long triggerRequestId;

    /** 派生医嘱 based_on 表名：药品为 {@code med_medication_request}，诊疗为 {@code wkf_service_request}。 */
    @Schema(description = "派生医嘱 based_on 表名")
    private String basedOnTable;

    /** 派生医嘱 based_on 主键；与 triggerRequestId 同值，CREATE 暂存前可为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "派生医嘱 based_on 主键")
    private Long basedOnRequestId;
}
