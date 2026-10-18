/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 住院退药患者队列行 DTO（IF-IPR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院退药患者队列行")
public class InpatientReturnPatientRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 住院就诊主键；须为在院且存在退药发放事实的 encounter。 */
    @Schema(description = "就诊标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄文本。 */
    @Schema(description = "年龄文本")
    private String age;

    /** 身份证（脱敏）。 */
    @Schema(description = "身份证（脱敏）")
    private String idCard;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    private String bedName;

    /** 住院号（encounterBusNo）。 */
    @Schema(description = "住院号")
    private String encounterNo;

    /** 聚合退药态 code：PENDING_REFUND / REFUNDED。 */
    @Schema(description = "聚合退药态 code")
    private String refundEnum;

    /** 聚合退药态展示。 */
    @Schema(description = "聚合退药态展示")
    private String refundEnumText;

    /**
     * 费用性质展示名（就诊优先合同名；与门诊发药/医生站卡片 {@code contractDisplay} 同口径）。
     * <p>无合同时为空，前端回落「自费」。</p>
     */
    @Schema(description = "费用性质展示名")
    private String contractDisplay;
}
