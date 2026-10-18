/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 医生工作量卡片行（仅卡片指标；fin_type/号源明细在弹窗懒加载 IF-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "医生工作量卡片行")
public class PractitionerWorkloadDoctorCardDto {

    /** 医生主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医生主键")
    private Long practitionerId;

    /** 医生姓名（开单/接诊医生展示；卡片墙可含下拉外医生）。 */
    @Schema(description = "医生姓名")
    private String practitionerName;

    /** 卡片外显合计（接诊/处方/诊疗/开单金额）。 */
    @Schema(description = "卡片外显合计")
    private PractitionerWorkloadSummaryTotalsDto summaryTotals;
}
