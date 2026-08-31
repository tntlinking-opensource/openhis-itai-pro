/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院平台在院对齐（诊断/修复）响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院平台在院对齐响应")
public class AlignInpatientPlatformVisitResponse {

    @Schema(description = "本地 map 的 mdtrt_id")
    private String localMdtrtId;

    @Schema(description = "5201 同人在院 mdtrt 列表")
    private List<String> platformInHospitalMdtrtIds = new ArrayList<>();

    @Schema(description = "不一致码")
    private String mismatchCode;

    @Schema(description = "建议动作")
    private String advice;

    @Schema(description = "是否已执行修复")
    private boolean repaired;

    @Schema(description = "修复摘要")
    private String repairSummary;
}
