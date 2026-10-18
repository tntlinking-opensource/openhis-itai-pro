/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询医技执行记录请求（AS-MT-EXE-001 IF-MT-EXE-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询医技执行记录请求")
public class PageMedicalTechProcedureRecordsRequest extends AbstractHealthPageRequest {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 就诊类别 scope。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 可选：按来源请求缩窄。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键")
    private Long requestId;

    /** 可选：执行状态码过滤。 */
    @Schema(description = "执行状态码")
    private String statusEnum;
}
