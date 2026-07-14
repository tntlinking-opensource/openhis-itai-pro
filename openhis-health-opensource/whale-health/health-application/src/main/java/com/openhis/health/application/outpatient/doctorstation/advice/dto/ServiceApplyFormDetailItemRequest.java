/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-21 / IF-IP-DOR-23：申请单明细行（部位/样本引用）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单明细行（部位/样本引用）")
public class ServiceApplyFormDetailItemRequest {

    /** 目标表名：dia_body_structure 或 adm_specimen_definition。 */
    @NotNull(message = "明细目标表名不能为空")
    @Schema(description = "目标表名：dia_body_structure 或 adm_specimen_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetTable;

    /** 目标主键。 */
    @NotNull(message = "明细目标主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    /** 与该目标同行的补充说明。 */
    @Schema(description = "与该目标同行的补充说明")
    private String remark;
}
