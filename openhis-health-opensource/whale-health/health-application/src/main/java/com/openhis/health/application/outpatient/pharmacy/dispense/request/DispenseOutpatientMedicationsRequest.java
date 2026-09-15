/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.DispenseMedicationLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊核发（发药扣库）请求（IF-OPP-05）。
 *
 * <p>领域推进发放完成并触发库存最小单位扣减；医保上传为可选协作。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊核发请求")
public class DispenseOutpatientMedicationsRequest {

    /** 就诊上下文主键。 */
    @NotNull(message = "就诊不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 用户勾选的待发药完成行（仅须发放单 id，数量以领域实体为准）。 */
    @NotEmpty(message = "提交行不能为空")
    @Valid
    /** 勾选发放行。 */
    @Schema(description = "勾选发放行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DispenseMedicationLineSubmitDto> lines;
}
