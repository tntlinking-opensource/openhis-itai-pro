/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

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

    /** 方级开立时间（{@code req_opened_time} 聚合；空行回落 {@code req_authored_time}）。 */
    @Schema(description = "开立时间（req_opened_time）")
    private LocalDateTime reqOpenedTime;

    /** 开立人姓名（由方级 requester_id 经 Practitioner 批量 enrich，供 IF-DOR-39 列表展示与关键字过滤）。 */
    @Schema(description = "开立人姓名")
    private String authoredByName;

    /** 方摘要。 */
    @Schema(description = "方摘要")
    private String summaryText;

    /** 药名列表。 */
    @Schema(description = "药名列表")
    private List<String> medicationNames = new ArrayList<>();
}
