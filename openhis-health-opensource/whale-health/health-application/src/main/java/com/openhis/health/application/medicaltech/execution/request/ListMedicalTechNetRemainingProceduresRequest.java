/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询医技净剩可取消执行记录请求（AS-MT-EXE-001 IF-MT-EXE-03B）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询医技净剩可取消执行记录请求")
public class ListMedicalTechNetRemainingProceduresRequest {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 就诊类别 scope。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 可选：按来源请求缩窄；与 requestTable 同传。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键")
    private Long requestId;

    /** 可选：来源表；与 requestId 同传。 */
    @Schema(description = "来源表")
    private String requestTable;
}
