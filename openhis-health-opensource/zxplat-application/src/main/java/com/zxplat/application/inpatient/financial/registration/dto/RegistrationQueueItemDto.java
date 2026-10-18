/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 住院登记待办列表行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记待办列表行")
public class RegistrationQueueItemDto {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 患者 ID。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 费用性质展示名（就诊优先合同名；与预交金/护士站队列 contractDisplay 同口径）。 */
    @Schema(description = "费用性质展示名")
    private String contractDisplay;

    /**
     * 费用性质意向（医生转住院提示；{@code ChargeIntent} code）。
     *
     * <p>待登记（PLANNED）时合同可能尚未开户；本字段提示款台医保/自费。</p>
     */
    @Schema(description = "费用性质意向：INSURANCE=医保 / SELF_PAY=自费")
    private String chargeIntent;

    /** 费用性质意向展示文案（医保/自费）。 */
    @Schema(description = "费用性质意向展示文案")
    private String chargeIntentText;

    /**
     * 医保险种类别码（国家医保：310 职工 / 390 城乡居民等）。
     *
     * <p>来自就诊医保账户 {@code adm_account.insutype}；账户空时回落读卡缓存/预结算，
     * 与医生站/护士站队列同口径。</p>
     */
    @Schema(description = "医保险种类别码（310 职工 / 390 居民等）")
    private String insutype;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示文案。 */
    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

    /** 科室 ID。 */
    @Schema(description = "科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病区 ID。 */
    @Schema(description = "病区 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床位 ID。 */
    @Schema(description = "床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    private String bedName;

    /** 申请/入院时间。 */
    @Schema(description = "申请/入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    private String inWayCode;

    /** 入院方式展示文案。 */
    @Schema(description = "入院方式展示文案")
    private String inWayCodeText;

    /** 来源门诊就诊 ID。 */
    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ambEncounterId;

    /** 是否可取消登记。 */
    @Schema(description = "是否可取消登记")
    private Boolean cancelable;

    /** 是否已在医保平台完成入院登记（2401）。 */
    @Schema(description = "是否已在医保平台完成入院登记（2401）")
    private Boolean insurancePlatformRegistered;

    /** 是否允许自费转医保（BR-IP-REG-019）。 */
    @Schema(description = "是否允许自费转医保")
    private Boolean switchToInsuranceEnabled;

    /** 是否允许医保转自费（IF-IP-REG-09）：在院医保入院且无已结算费用。 */
    @Schema(description = "是否允许医保转自费")
    private Boolean switchToSelfPayEnabled;
}
