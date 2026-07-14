/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 挂号预结算请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号预结算请求")
public class PreRegisterSettlementRequest {

    /** 患者ID（业务字段）。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String name;

    /** 性别编码。 */
    @Schema(description = "性别")
    private String gender;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /** 手机号。 */
    @Schema(description = "手机号")
    private String phone;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /** 费用性质 ID。 */
    @Schema(description = "费用性质ID")
    private Long contractId;

    /** 科室 ID。 */
    @Schema(description = "科室ID")
    private Long organizationId;

    /** 医生 ID。 */
    @Schema(description = "医生ID")
    private Long practitionerId;

    /** 号源类型 ID。 */
    @Schema(description = "号源类型ID")
    private Long serviceTypeId;

    /** 关联预约主键（预约转挂号时传入）。 */
    @Schema(description = "预约ID")
    private Long appointmentId;

    /** 指定占用的号源主键（窗口从号源日历点选时传入）。 */
    @Schema(description = "号源ID")
    private Long slotId;

    /** 就诊凭证类型（医保读卡）。 */
    @Schema(description = "就诊凭证类型（医保读卡）")
    private String ybMediumType;

    /** 就诊凭证编号（医保读卡）。 */
    @Schema(description = "就诊凭证编号（医保读卡）")
    private String ybCardInfo;

    /** 人员编号（1101）。 */
    @Schema(description = "人员编号（1101）")
    private String psnNo;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 医疗类别，默认 11 普通门诊。 */
    @Schema(description = "医疗类别，默认 11 普通门诊")
    private String medType;

    /** 挂号费是否走医保（2203→2206 预结算；缴费时 2207）。 */
    @Schema(description = "挂号费是否走医保预结算")
    private Boolean ybRegisterFee;

    /** 医保预结算是否使用个人账户（acct_used_flag=1）；缺省 true。 */
    @Schema(description = "医保预结算是否使用个人账户")
    private Boolean ybUseAcctPay;
}
