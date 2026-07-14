/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-39：患者历史中药处方摘要行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者历史中药处方摘要")
public class TcmRxHistoryRowDto {

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 来源就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源就诊主键")
    private Long encounterId;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    private LocalDateTime authoredAt;

    /** 方摘要。 */
    @Schema(description = "方摘要")
    private String summaryText;

    /** 药名列表。 */
    @Schema(description = "药名列表")
    private List<String> medicationNames = new ArrayList<>();
}
