/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 打印医技费用清单请求（AS-MT-FEE-001 IF-MT-FEE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印医技费用清单请求")
public class PrintMedicalTechFeeListRequest {

    /** 就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 就诊类别 scope：{@code OUTPATIENT}/{@code INPATIENT}。
     */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /**
     * 勾选收费明细主键子集；空或 null 表示打印当前本科相关过滤全集。
     */
    @Schema(description = "勾选收费明细主键；空=全集")
    private List<Long> chargeItemIds;

    /**
     * 打印场景码（与 busNo 二选一策略由打印运行时定；本 IF 经 Mapper 写入 PreparePrintRequest）。
     */
    @Schema(description = "打印场景码")
    private String scenarioCode;
}
