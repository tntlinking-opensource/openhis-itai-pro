/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 医技执行单行（诊疗/耗材同构，AS-MT-EXE-001 IF-MT-EXE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技执行单行")
public class MedicalTechExecutionLineDto {

    /** 请求主键（诊疗 ServiceRequest / 耗材 DeviceRequest）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键")
    private Long requestId;

    /** 请求来源表持久化名。 */
    @Schema(description = "请求来源表")
    private String requestTable;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 项目编码。 */
    @Schema(description = "项目编码")
    private String itemCode;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 计划执行次数（诊疗）。 */
    @Schema(description = "计划执行次数")
    private Integer plannedExecutionCount;

    /** 本站已完成次数（诊疗；仅计 loginTech org_id）。 */
    @Schema(description = "已完成次数")
    private Integer completedCount;

    /** 本站已取消次数（诊疗；仅计 loginTech org_id）。 */
    @Schema(description = "已取消次数")
    private Integer cancelledCount;

    /** 账单金额合计；无账单时为目录现价×数量预览（非费用清单查询）。 */
    @Schema(description = "金额合计（账单或目录预览）")
    private BigDecimal chargeAmount;

    /** 首条收费状态码。 */
    @Schema(description = "收费状态码")
    private String chargeStatusEnum;

    /** 首条收费状态展示。 */
    @Schema(description = "收费状态展示")
    private String chargeStatusEnumText;

    /** 发放状态码（耗材）。 */
    @Schema(description = "发放状态码")
    private String dispenseStatusEnum;

    /** 发放状态展示（耗材）。 */
    @Schema(description = "发放状态展示")
    private String dispenseStatusEnumText;

    /** 签发批号（耗材）。 */
    @Schema(description = "签发批号")
    private String lotNumber;

    /** 单价：优先 ChargeItem 落库价；无账单时目录 SALE/参考售价现价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 申请单号/处方号。 */
    @Schema(description = "申请单号")
    private String prescriptionNo;

    /** 部位/样本等申请单明细摘要。 */
    @Schema(description = "申请单明细摘要")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 是否可勾选执行。 */
    @Schema(description = "是否可勾选执行")
    private Boolean selectable;

    /** 诊疗类目 code（检验为 {@code 22}，供采血贴列表过滤）。 */
    @Schema(description = "诊疗类目 code")
    private String serviceCategoryCode;

    /**
     * 合管检验设备主键。
     * 同一设备的多行只打第一张采血贴。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "合管检验设备主键")
    private Long specimenTubeMergeDeviceId;

    /** 医嘱优先级 code（加急=EM；采血贴印「急」）。 */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;

    /** 主发放行主键（耗材勾选执行用）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主发放行主键")
    private Long dispenseId;
}
