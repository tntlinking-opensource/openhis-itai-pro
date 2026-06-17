/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
/** 在院患者列表行。 */
@Schema(description = "在院患者列表行")
public class PatientQueueItemDto {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 就诊默认个人现金账户主键（执行/费用锚点，与 IF-IP-EXE-02 回传一致）。 */
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
    @Schema(description = "床位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedName;

    /** 科室主键。 */
    @Schema(description = "科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病情/优先级 code。 */
    @Schema(description = "病情/优先级 code")
    private String priorityEnum;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 入科时间。 */
    @Schema(description = "入科时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime admissionTime;

    /** 累计费用。 */
    @Schema(description = "累计费用")
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /** 欠费金额。 */
    @Schema(description = "欠费金额")
    private BigDecimal arrearsAmount;

    /** 展示用账户余额。 */
    @Schema(description = "展示用账户余额")
    private BigDecimal accountBalance;

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

    /** 诊断一行摘要。 */
    @Schema(description = "诊断一行摘要（多条以分号拼接）")
    private String diagnosisSummary;

    /** 诊断展示列表。 */
    @Schema(description = "诊断展示列表（由读模型快照解析，非 JSON）")
    private List<String> diagnosisList;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wubiCode;
}
