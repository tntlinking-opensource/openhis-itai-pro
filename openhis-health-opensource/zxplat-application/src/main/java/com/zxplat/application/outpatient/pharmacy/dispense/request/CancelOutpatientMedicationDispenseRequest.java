/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 作废门诊药品发放请求（IF-OPP-06）。
 *
 * <p>须在允许作废的窗口内携带未执行原因码，满足审计闭环。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "作废门诊药品发放请求")
public class CancelOutpatientMedicationDispenseRequest {

    /** 就诊上下文主键。 */
    @NotNull(message = "就诊不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 每条待作废发放单及对应未发药原因枚举 code。 */
    @NotEmpty(message = "提交行不能为空")
    @Valid
    /** 作废行集合。 */
    @Schema(description = "作废行集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CancelMedicationDispenseLineSubmitDto> lines;
}
