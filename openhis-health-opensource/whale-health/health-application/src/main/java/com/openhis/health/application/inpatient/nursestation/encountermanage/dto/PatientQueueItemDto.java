/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 在院患者列表卡片行（IF-IP-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "在院患者列表行")
public class PatientQueueItemDto implements InpatientAccountBalanceDisplayDtoApplier.AccountBalanceDisplayTarget {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 就诊默认自费账户主键。 */
    @Schema(description = "就诊默认自费账户主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示文案。 */
    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /** 年龄文本。 */
    @Schema(description = "年龄文本")
    private String ageText;

    /** 病区主键。 */
    @Schema(description = "病区主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String houseName;

    /** 床位主键。 */
    /** 床位主键（活动床；无活动床时可为末次已完成床，仅展示）。 */
    @Schema(description = "床位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /**
     * 床号展示。
     *
     * <p>无活动床（召回未占床）时为「原{床名}（未占床）」。</p>
     */
    @Schema(description = "床号；未占床时含「原…（未占床）」")
    private String bedName;

    /** 科室主键。 */
    @Schema(description = "科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /**
     * 费用性质/医保类型展示：有医保账户优先其合同名，否则登记个人现金户合同名。
     */
    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;

    /** 身份证号（完整号，不脱敏）。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /**
     * 医保人员编号（平台 psn_no）；未建档映射时可为 null。
     */
    @Schema(description = "医保人员编号")
    private String insuranceNo;

    /** 患者住址全文。 */
    @Schema(description = "患者住址")
    private String address;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    private String patientPhone;

    /** 病人院内编码（病人 ID）。 */
    @Schema(description = "病人院内编码")
    private String patientBusNo;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    private String inWayCode;

    /** 入院途径展示。 */
    @Schema(description = "入院途径展示")
    private String inWayCodeText;

    /** 入院状态/病情展示。 */
    @Schema(description = "入院状态展示")
    private String priorityEnumText;

    /** 险种类别 code。 */
    @Schema(description = "险种类别 code")
    private String insutype;

    /** 医疗类别 code。 */
    @Schema(description = "医疗类别 code")
    private String medType;

    /** 医疗类别展示。 */
    @Schema(description = "医疗类别展示")
    private String medTypeDisplay;

    /** 病情/优先级 code。 */
    @Schema(description = "病情/优先级 code")
    private String priorityEnum;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 入科时间。 */
    @Schema(description = "入科时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime admissionTime;

    /** 出院时间。 */
    @Schema(description = "出院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime endTime;

    /** 累计费用。 */
    @Schema(description = "累计费用")
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /** 欠费金额。 */
    @Schema(description = "欠费金额")
    private BigDecimal arrearsAmount;

    /** 展示用账户余额（与可用余额一致）。 */
    @Schema(description = "展示用账户余额（与可用余额一致）")
    private BigDecimal accountBalance;

    /** 账户余额展示模式。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

    /** 预交金净值。 */
    @Schema(description = "预交金净值")
    private BigDecimal prepayNetBalance;

    /** 待结算费用合计。 */
    @Schema(description = "待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    /** 可用余额。 */
    @Schema(description = "可用余额")
    private BigDecimal availableBalance;

    /** 账户余额主展示文案。 */
    @Schema(description = "账户余额主展示文案")
    private String accountBalanceSummaryText;

    /** 账户余额副展示文案。 */
    @Schema(description = "账户余额副展示文案")
    private String accountBalanceSubText;

    /** 低余额预警。 */
    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;

    /** 待校对条数。 */
    @Schema(description = "待校对条数")
    private Integer pendingAuditCount;

    /** 待执行条数。 */
    @Schema(description = "待执行条数")
    private Integer pendingExecuteCount;

    /** 主治医生姓名摘要。 */
    @Schema(description = "主治医生姓名摘要")
    private String attendingDoctorName;

    /** 责任护士姓名摘要。 */
    @Schema(description = "责任护士姓名摘要")
    private String primaryNurseName;

    /** 诊断一行摘要（多条以分号拼接）。 */
    @Schema(description = "诊断一行摘要（多条以分号拼接）")
    private String diagnosisSummary;

    /** 诊断展示列表（由读模型快照解析，非 JSON）。 */
    @Schema(description = "诊断展示列表（由读模型快照解析，非 JSON）")
    private List<String> diagnosisList;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wubiCode;

    /** 护理级别医嘱主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理级别医嘱主键")
    private Long nursingLevelServiceRequestId;

    /** 护理级别 activityId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理级别 activityId")
    private Long nursingLevelActivityId;

    /** 护理级别展示名。 */
    @Schema(description = "护理级别展示名")
    private String nursingLevelName;

    /** 护理级别医嘱状态 code。 */
    @Schema(description = "护理级别医嘱状态 code")
    private String nursingLevelStatusEnum;
}
