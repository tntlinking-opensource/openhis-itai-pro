/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.encountermanage.dto;

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
 * 住院医生站在院患者列表卡片行（AS-IP-DOR-001 / IF-IP-DOR-01）。
 *
 * <p>由 {@code InpatientInfoReadModel} 与账户、诊断快照解析、展示增补装配；
 * 诊断字段为解析后的列表/摘要，禁止 API 透传 {@code diagnosisJson} 原文。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站在院患者列表行")
public class DoctorStationPatientQueueItemDto implements InpatientAccountBalanceDisplayDtoApplier.AccountBalanceDisplayTarget {

    /**
     * 住院就诊主键：医生站患者卡片与后续诊断/医嘱操作的锚点。
     */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 住院号/就诊业务号：列表检索与床头卡展示。
     */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /**
     * 就诊状态编码：与 {@link com.openhis.health.domain.share.enums.EncounterStatus} code 一致。
     */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /**
     * 就诊状态中文：经 {@code InpatientEncounterStatusDisplayUtil} 推导，含转科/出院类型语义。
     */
    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

    /**
     * 患者主键。
     */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 患者姓名：读模型优先，缺失时回落展示覆盖层。
     */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 性别编码：与 {@link com.openhis.health.domain.share.enums.AdministrativeGender#getCode()} 一致。
     */
    @Schema(description = "性别 code")
    private String genderEnum;

    /**
     * 性别中文展示。
     */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /**
     * 年龄展示文本（读模型已格式化）。
     */
    @Schema(description = "年龄文本")
    private String ageText;

    /**
     * 病区主键。
     */
    @Schema(description = "病区主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /**
     * 病区名称。
     */
    @Schema(description = "病区名称")
    private String wardName;

    /**
     * 病房名称：由床位 bus_no 前缀匹配病房位置解析，读模型未投影。
     */
    @Schema(description = "病房名称")
    private String houseName;

    /**
     * 床位主键。
     */
    @Schema(description = "床位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /**
     * 床号/床位展示名。
     */
    @Schema(description = "床号")
    private String bedName;

    /**
     * 当前科室主键（列表按登录科室收窄）。
     */
    @Schema(description = "科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /**
     * 科室名称。
     */
    @Schema(description = "科室名称")
    private String organizationName;

    /**
     * 病情/优先级编码：读模型未投影时由就诊主档批量回填。
     */
    @Schema(description = "病情/优先级 code")
    private String priorityEnum;

    /**
     * 入院/就诊开始时间（读模型 {@code startTime}）。
     */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /**
     * 出院/就诊结束时间（读模型 {@code endTime}；在院未出院时为 null）。
     */
    @Schema(description = "出院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime endTime;

    /**
     * 入科时间：读模型当前未投影，与护士站列表口径一致为 null。
     */
    @Schema(description = "入科时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime admissionTime;

    /**
     * 累计费用（读模型快照）。
     */
    @Schema(description = "累计费用")
    private BigDecimal totalAmount;

    /**
     * 已支付金额（读模型快照）。
     */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /**
     * 欠费金额（读模型快照）。
     */
    @Schema(description = "欠费金额")
    private BigDecimal arrearsAmount;

    /**
     * 展示用账户余额：财务口径动态计算（库内余额 − 待结算），非直出库字段（BR-IP-DOR-006）。
     */
    @Schema(description = "展示用账户余额")
    private BigDecimal accountBalance;

    /** 账户余额展示模式。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

    /** 库内账户余额。 */
    @Schema(description = "库内账户余额")
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

    /**
     * 低余额预警：展示余额低于 500 元时为 true。
     */
    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;

    /**
     * 主治医生姓名摘要。
     */
    @Schema(description = "主治医生姓名摘要")
    private String attendingDoctorName;

    /**
     * 责任护士姓名摘要。
     */
    @Schema(description = "责任护士姓名摘要")
    private String primaryNurseName;

    /**
     * 诊断一行摘要：多条诊断以分号拼接，供列表紧凑展示。
     */
    @Schema(description = "诊断一行摘要（多条以分号拼接）")
    private String diagnosisSummary;

    /**
     * 诊断展示列表：由 {@code diagnosisJson} 快照解析，非 JSON 透传。
     */
    @Schema(description = "诊断展示列表（由读模型快照解析，非 JSON）")
    private List<String> diagnosisList;

    /**
     * 拼音检索码：读模型未投影，由患者扩展档批量回填。
     */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /**
     * 五笔检索码：读模型未投影，由患者扩展档批量回填。
     */
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

    /**
     * 费用性质/医保类型展示：由登记账户 contractNo 解析合同名称，与预交金队列 contractDisplay 对齐。
     */
    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;

    /**
     * 险种类型码（国家医保：310 职工 / 390 城乡居民等）：来自就诊医保账户 {@code adm_account.insutype}。
     */
    @Schema(description = "险种类型码（310 职工 / 390 城乡居民等）")
    private String insutype;
}
