/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 队列行展示增补：读模型未投影字段由 App 批量回填。
 */
@Data
@Accessors(chain = true)
@Schema(description = "队列展示增补字段")
public class NurseStationQueueDisplayEnrichment {

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String houseName;

    /** 拼音检索码。 */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /** 五笔检索码。 */
    @Schema(description = "五笔码")
    private String wubiCode;

    /** 身份证号（完整号，不脱敏）。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /** 医保人员编号（平台 psn_no）。 */
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
}
