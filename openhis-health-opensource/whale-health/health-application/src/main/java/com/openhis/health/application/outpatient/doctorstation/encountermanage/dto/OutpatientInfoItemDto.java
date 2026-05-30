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

package com.openhis.health.application.outpatient.doctorstation.encountermanage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 医生站门诊信息列表行 DTO（与 OutpatientInfo 读模型展示字段对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-门诊信息列表行")
public class OutpatientInfoItemDto {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键（列表锚点）")
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 就诊类别（与 EncounterClass.sortOrder 对齐的整型展示）。 */
    @Schema(description = "就诊类别整型展示（来源 class_enum 映射）")
    private Integer classEnum;

    /** 就诊状态编码。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 状态展示文本。 */
    @Schema(description = "就诊状态展示文本")
    private String statusEnumText;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别编码（与 {@link com.openhis.health.domain.share.enums.AdministrativeGender#getCode()} 一致，直接透传读模型）。 */
    @Schema(description = "性别编码（AdministrativeGender：MALE/FEMALE/UNKNOWN）")
    private String genderEnum;

    /** 性别展示文案（由编码解析 AdministrativeGender.display）。 */
    @Schema(description = "性别展示文本")
    private String genderEnumText;

    /** 出生日期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "出生日期")
    private LocalDateTime birthDate;

    /** 年龄文本。 */
    @Schema(description = "年龄展示文本")
    private String ageText;

    /** 证件号。 */
    @Schema(description = "证件号")
    private String idCard;

    /** 电话。 */
    @Schema(description = "联系电话")
    private String phone;

    /** 科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室主键")
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 接诊医生主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "接诊医生主键")
    private Long doctorId;

    /** 接诊医生姓名。 */
    @Schema(description = "接诊医生姓名")
    private String doctorName;

    /** 服务类型主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务类型主键")
    private Long serviceTypeId;

    /** 服务类型名称。 */
    @Schema(description = "服务类型名称")
    private String serviceTypeName;

    /** 就诊开始时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "就诊开始时间（挂号/预约）")
    private LocalDateTime startTime;

    /** 接诊时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接诊时间")
    private LocalDateTime receptionTime;

    /** 结束时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "就诊结束时间")
    private LocalDateTime endTime;

    /** 当日序号。 */
    @Schema(description = "当日展示序号")
    private Integer displayOrder;

    /** 初复诊文本。 */
    @Schema(description = "初诊/复诊展示文本")
    private String firstEnumText;

    /** 费用总额。 */
    @Schema(description = "门诊费用总额")
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /** 已退款金额。 */
    @Schema(description = "已退款金额")
    private BigDecimal refundAmount;

    /** 诊断快照（由读模型 JSON 列解析，禁止对外透出原始 JSON 串）。 */
    @Schema(description = "诊断列表")
    private List<EncounterDiagnosisRowDto> diagnoses;

    /** 分诊级别整型展示。 */
    @Schema(description = "分诊级别（急诊场景，可能由字符串码映射为整数展示）")
    private Integer triageLevelEnum;

    /** 分诊时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "分诊时间")
    private LocalDateTime triageTime;
}
