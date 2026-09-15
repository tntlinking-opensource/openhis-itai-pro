/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 点评记录列表行（IF-PRV-02）。
 *
 * <p>对外列表列与「处方点评工作表」数据列对齐（无序号）；字段名用我方命名。
 * {@code id} 仅作行键/详情锚点，不进导出列。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录列表行（工作表同列）")
public class PrescriptionReviewListRowDto {

    /** 点评主键（行键；不进工作表列）。 */
    @Schema(description = "点评主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 处方号（工作表：处方编号）。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 患者年龄展示（工作表：年龄(岁)）。 */
    @Schema(description = "患者年龄展示")
    private String patientAgeText;

    /** 诊断并集展示（工作表：诊断）。 */
    @Schema(description = "诊断名称")
    private String diagnosisName;

    /** 药品品种数（distinct medicationId）。 */
    @Schema(description = "药品品种数")
    private Integer drugVarietyCount;

    /** 是否含抗菌药（0/1）。 */
    @Schema(description = "是否含抗菌药（0/1）")
    private Integer antibioticFlag;

    /** 是否含注射剂（0/1）。 */
    @Schema(description = "是否含注射剂（0/1）")
    private Integer injectFlag;

    /** 国家基本药物品种数。 */
    @Schema(description = "国家基本药物品种数")
    private Integer basicDrugVarietyCount;

    /** 药品通用名数（distinct 目录 name）。 */
    @Schema(description = "药品通用名数")
    private Integer genericNameCount;

    /** 处方金额（元，DISPLAY 2 位）。 */
    @Schema(description = "处方金额（元）")
    private BigDecimal prescriptionAmount;

    /** 开方医师姓名（工作表：处方医师）。 */
    @Schema(description = "开方医师姓名")
    private String orderingPractitionerName;

    /** 是否合理。 */
    @Schema(description = "是否合理")
    private Boolean reasonableFlag;

    /** 是否合理展示文案（列表 Tag；导出用 0/1）。 */
    @Schema(description = "是否合理文案")
    private String reasonableText;

    /** 原因（工作表：存在问题；字段名仍为 reason）。 */
    @Schema(description = "原因（存在问题）")
    private String reason;

    /** 点评业务号（PRV；详情「点评编号」；不进工作表列）。 */
    @Schema(description = "点评业务号")
    private String busNo;

    /** 点评状态 code（详情用；有效行固定 REVIEWED）。 */
    @Schema(description = "点评状态 code")
    private String statusEnum;

    /** 点评状态展示文案（详情用）。 */
    @Schema(description = "点评状态文案")
    private String statusText;

    /** 备注（详情用；不进工作表列）。 */
    @Schema(description = "备注")
    private String remark;

    /** 点评人主键（详情用）。 */
    @Schema(description = "点评人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewerId;

    /** 点评人姓名（详情用）。 */
    @Schema(description = "点评人姓名")
    private String reviewerName;

    /** 点评时间（yyyy-MM-dd HH:mm:ss；详情用）。 */
    @Schema(description = "点评时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private String reviewedAt;
}
