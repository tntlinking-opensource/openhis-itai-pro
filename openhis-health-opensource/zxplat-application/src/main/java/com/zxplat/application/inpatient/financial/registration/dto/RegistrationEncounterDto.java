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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 登记弹窗住院就诊信息 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "登记住院就诊信息")
public class RegistrationEncounterDto {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 状态 code。 */
    @Schema(description = "状态 code")
    private String statusEnum;

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

    /** 分床后床位 ID。 */
    @Schema(description = "分床后床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /** 分床后床位名称。 */
    @Schema(description = "分床后床位名称")
    private String bedName;

    /** 入院来源 code。 */
    @Schema(description = "入院来源 code")
    private String admitSourceCode;

    /** 入院来源展示文案。 */
    @Schema(description = "入院来源展示文案")
    private String admitSourceCodeText;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    private String inWayCode;

    /** 入院方式展示文案。 */
    @Schema(description = "入院方式展示文案")
    private String inWayCodeText;

    /** 入院/申请时间。 */
    @Schema(description = "入院/申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 来源门诊就诊 ID。 */
    @Schema(description = "来源门诊就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ambEncounterId;

    /** 主诊断定义 ID。 */
    @Schema(description = "主诊断定义 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryConditionDefinitionId;

    /** 主诊断定义名称。 */
    @Schema(description = "主诊断定义名称")
    private String primaryConditionDefinitionName;

    /** 病情优先级 code。 */
    @Schema(description = "病情优先级 code")
    private String priorityEnum;

    /** 源门诊接诊医生姓名（门诊转住院只读展示）。 */
    @Schema(description = "源门诊接诊医生姓名（门诊转住院只读展示）")
    private String outpatientDoctorName;

    /** 主治医生医务人员 ID。 */
    @Schema(description = "主治医生医务人员 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long attendingDoctorId;

    /** 主治医生姓名。 */
    @Schema(description = "主治医生姓名")
    private String attendingDoctorName;

    /**
     * 费用性质（合同）ID。
     *
     * <p>已登记就诊：优先医保账户 {@code contract_no} 对应合同，否则回落个人现金户；
     * 未开户（如仅医生申请）时为 null，前端可默认下拉第一项。</p>
     */
    @Schema(description = "费用性质（合同）ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /**
     * 医保险种类别码（国家医保：310 职工 / 390 城乡居民等）。
     *
     * <p>已开医保户时来自 {@code adm_account.insutype}；否则回落读卡缓存/预结算；
     * 自费或无码时为 null，登记弹窗「医保类别」只读展示。</p>
     */
    @Schema(description = "医保险种类别码（310 职工 / 390 居民等）")
    private String insutype;

    /**
     * 费用性质意向（医生转住院提示；{@code ChargeIntent} code）。
     *
     * <p>{@code INSURANCE}=医保 / {@code SELF_PAY}=自费；可空。非真实费用性质，登记合同仍以收费员选择为准。</p>
     */
    @Schema(description = "费用性质意向：INSURANCE=医保 / SELF_PAY=自费")
    private String chargeIntent;

    /**
     * 费用性质意向展示文案（医保/自费；无意向时为 null）。
     */
    @Schema(description = "费用性质意向展示文案")
    private String chargeIntentText;

    /**
     * 意向预交金总额（门诊转住院 DRAFT 回显；收费员可改）。
     *
     * <p>来自最新 {@code HOSPITAL_DEPOSIT + DRAFT} 的 {@code displayAmount}；无草稿时为 null。</p>
     */
    @Schema(description = "意向预交金总额（DRAFT 回显）")
    private BigDecimal advanceDepositAmount;

    /**
     * 意向预交金草稿支付主键（可选，供排查；提交以金额+明细为准）。
     */
    @Schema(description = "意向预交金草稿支付主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;
}
