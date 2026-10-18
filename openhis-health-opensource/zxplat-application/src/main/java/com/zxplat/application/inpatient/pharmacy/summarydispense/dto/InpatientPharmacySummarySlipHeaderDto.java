/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 待核发汇总单头（IF-IPS-01/02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总单头")
public class InpatientPharmacySummarySlipHeaderDto {

    /** 汇总单号（与 {@code bus_no} 同义）。 */
    @Schema(description = "汇总单号")
    private String busNo;

    /** 护士站发送时间。 */
    @Schema(description = "申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime applyTime;

    /** 药房核发时间（未核发为空）。 */
    @Schema(description = "发药时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime dispenseTime;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;

    /** 发放药房名称。 */
    @Schema(description = "发放药房名称")
    private String purposeLocationName;

    /**
     * 申领人登录科室主键。点汇总/发送时写入；历史单为空时打印回落申领人默认登录科室。
     */
    @Schema(description = "申领人登录科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long consumingOrganizationId;

    /**
     * 申领科室展示名（申领人登录科室）。详情打印用，列表不填。
     */
    @Schema(description = "申领科室")
    private String requestingDepartmentName;

    /** 申请人（护士）主键。 */
    @Schema(description = "申请人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /**
     * 申领科室名称（病区主挂靠科室；打印/详情展示）。
     *
     * <p>口径与护士站 IF-IP-SUM-03 同源：发放行就诊所属病区主挂靠科室名；
     * 未挂靠时回落就诊当前科室；多科室顿号拼接。</p>
     */
    @Schema(description = "申领科室名称（病区所属科室）")
    private String applicantDepartmentName;

    /** 领药人主键（核发后才有值）。 */
    @Schema(description = "领药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 领药人姓名。 */
    @Schema(description = "领药人姓名")
    private String receiverName;

    /** Supply 审批态 code（中转展示，不作已发药门控）。 */
    @Schema(description = "审批态 code")
    private String supplyStatusEnum;

    /** 审批态展示（Supply 中转态，仅供排障）。 */
    @Schema(description = "Supply 审批态展示")
    private String supplyStatusEnumText;

    /** 是否已发药：仅以同号发放行全部为 COMPLETED 为准（BR-IPS-013）。 */
    @Schema(description = "是否已发药（发放表终态）")
    private Boolean summaryDispenseCompleted;

    /** 汇总发药门控展示：待核对 / 已发药。 */
    @Schema(description = "汇总发药门控展示")
    private String summaryDispenseStatusText;
}
