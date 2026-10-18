/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.dto;

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
@Schema(description = "医生站暂存前绑定解析明细行")
public class DoctorStationAdviceDraftBindItemDto {

    /** 绑定来源类型：USAGE / ACTIVITY。 */
    @Schema(description = "绑定来源类型：USAGE / ACTIVITY")
    private String bindType;

    /** 源定义主键；用法绑定为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源定义主键；用法绑定为空")
    private Long sourceItemId;

    /** 源展示名。 */
    @Schema(description = "源展示名")
    private String sourceItemName;

    /** 绑定目标定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定目标定义主键")
    private Long targetItemId;

    /** 绑定目标展示名。 */
    @Schema(description = "绑定目标展示名")
    private String targetItemName;

    /** 绑定目标产品定义表名。 */
    @Schema(description = "绑定目标产品定义表名")
    private String targetProductTableName;

    /** 数量分子。 */
    @Schema(description = "数量分子")
    private BigDecimal quantityNumerator;

    /** 数量分母。 */
    @Schema(description = "数量分母")
    private BigDecimal quantityDenominator;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer priority;

    /** 是否有效。 */
    @Schema(description = "是否有效")
    private Boolean activeFlag;

    /** 触发暂存变更行下标。 */
    @Schema(description = "触发暂存变更行下标")
    private Integer triggerLineIndex;

    /** 触发行操作码。 */
    @Schema(description = "触发行操作码")
    private String triggerLineOp;

    /** 触发主医嘱产品定义表名。 */
    @Schema(description = "触发主医嘱产品定义表名")
    private String triggerProductTableName;

    /** 触发主医嘱定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "触发主医嘱定义主键")
    private Long triggerDefinitionId;

    /** 触发主医嘱请求主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "触发主医嘱请求主键")
    private Long triggerRequestId;

    /**
     * 派生医嘱 based_on 表名。
     * <p>合管采血费独立 ServiceRequest 时为空（不挂 based_on）。</p>
     */
    @Schema(description = "派生医嘱 based_on 表名；采血费独立行为空")
    private String basedOnTable;

    /**
     * 派生医嘱 based_on 主键。
     * <p>合管采血费独立 ServiceRequest 时为空。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "派生医嘱 based_on 主键；采血费独立行为空")
    private Long basedOnRequestId;

    /** 合管检验设备主键；合管耗材派生时有值；采血费独立行为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "合管检验设备主键；采血费独立行为空")
    private Long specimenTubeMergeDeviceId;

    /** 合管组序号（从1起；未合管/采血费独立行为 null）。 */
    @Schema(description = "合管组序号（从1起；未合管/采血费独立行为null）")
    private Integer specimenTubeMergeGroupNo;
}
