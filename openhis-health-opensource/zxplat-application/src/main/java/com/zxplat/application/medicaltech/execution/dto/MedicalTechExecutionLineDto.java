/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
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

    /**
     * 申请业务号（诊疗 {@code wkf_service_request.bus_no}）。
     *
     * <p>采血贴条码与 LIS 外发 {@code applyNo} 同源；缺省前端回落 {@link #requestId}。</p>
     */
    @Schema(description = "申请业务号（采血贴/LIS applyNo）")
    private String busNo;

    /** 部位/样本等申请单明细摘要。 */
    @Schema(description = "申请单明细摘要")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /**
     * 是否可勾选（批量执行或取消执行共用勾选列）。
     *
     * <p>诊疗：尚可执行（净次 &lt; 计划）或存在净剩可取消（净次 &gt; 0）为 true；
     * 耗材：待发放可执行，或已配药/已发放可取消为 true。</p>
     */
    @Schema(description = "是否可勾选（尚可执行或净剩可取消）")
    private Boolean selectable;

    /**
     * 诊疗类目 code（检验为 {@code 22}，供采血贴列表过滤）。
     *
     * <p>合管派生「采血费」类目亦为 22，前端打采血贴时须按项目名排除，不得印条码。</p>
     */
    @Schema(description = "诊疗类目 code")
    private String serviceCategoryCode;

    /**
     * 合管检验设备主键。
     * 同一设备的多行只打第一张采血贴。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "合管检验设备主键")
    private Long specimenTubeMergeDeviceId;

    /** 合管组序号（从1起；未合管为null）。 */
    @Schema(description = "合管组序号（从1起；未合管为null）")
    private Integer specimenTubeMergeGroupNo;

    /** 医嘱优先级 code（加急=EM；采血贴印「急」）。 */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;

    /** 主发放行主键（耗材勾选执行用）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主发放行主键")
    private Long dispenseId;

    /** 开方医生主键（{@code requester_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开方医生主键")
    private Long requesterId;

    /** 开方医生姓名。 */
    @Schema(description = "开方医生")
    private String requesterName;

    /**
     * 开方科室主键。
     *
     * <p>优先账单 {@code requesting_org_id}；无账单回落就诊 {@code organization_id}。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开方科室主键")
    private Long requestingOrgId;

    /** 开方科室展示名。 */
    @Schema(description = "开方科室")
    private String requestingOrgName;

    /**
     * 执行科室主键（诊疗 {@code ServiceRequest.org_id}）。
     *
     * <p>共看组时供前端按 CT/DR 等执行科室筛项目；写库仍用登录科。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键")
    private Long executingOrgId;

    /** 执行科室展示名。 */
    @Schema(description = "执行科室")
    private String executingOrgName;
}
