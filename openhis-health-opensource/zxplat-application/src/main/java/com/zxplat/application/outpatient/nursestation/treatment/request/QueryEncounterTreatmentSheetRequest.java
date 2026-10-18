/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按就诊查询处置单（三列表同构）请求（IF-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按就诊查询处置单请求")
public class QueryEncounterTreatmentSheetRequest {

    /**
     * 当前选中就诊主键。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前选中就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 当前选中就诊主键。 */
    private Long encounterId;

    /**
     * 诊疗类目过滤码：仅缩小诊疗列表，药品与耗材列表忽略（BR-NUR-009）。
     */
    @Schema(description = "诊疗类目过滤码（仅缩小诊疗列表）")
    private String serviceCategoryCode;
}
