/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 汇总单头（IF-IP-SUM-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总单头")
public class NurseStationDispenseSummarySlipHeaderDto {

    @Schema(description = "汇总单号")
    /** 汇总单号。 */
    private String busNo;

    @Schema(description = "申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 申请时间。 */
    private LocalDateTime applyTime;

    /** 发药时间。 */
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

    /** 申请人主键。 */
    @Schema(description = "申请人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /**
     * 申领科室名称（病区主挂靠科室；打印/详情展示）。
     *
     * <p>口径：汇总单发放行就诊所属病区 {@code Location.organizationId} 对应科室名；
     * 病区未挂科室时回落就诊当前科室名；多病区多科室顿号拼接。</p>
     */
    @Schema(description = "申领科室名称（病区所属科室）")
    private String applicantDepartmentName;

    /** 领药人主键。 */
    @Schema(description = "领药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 领药人姓名。 */
    @Schema(description = "领药人姓名")
    private String receiverName;

    /** 审批态 code。 */
    @Schema(description = "审批态 code")
    private String supplyStatusEnum;

    /** 审批态展示。 */
    @Schema(description = "审批态展示")
    private String supplyStatusEnumText;

    /** 发放态 code。 */
    @Schema(description = "发放态 code")
    private String deliveryStatusEnum;

    /** 发放态展示。 */
    @Schema(description = "发放态展示")
    private String deliveryStatusEnumText;
}
