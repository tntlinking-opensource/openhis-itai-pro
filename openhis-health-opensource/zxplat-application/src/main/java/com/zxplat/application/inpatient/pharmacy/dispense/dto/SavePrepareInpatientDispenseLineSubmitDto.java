/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院保存配药提交行（IF-IPP-04）。
 *
 * <p>库房/批号/数量由执行侧写入聚合，本 DTO 不得承载改选字段。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院保存配药提交行")
public class SavePrepareInpatientDispenseLineSubmitDto {

    /** 待发配药发放行主键；须 {@code PREPARATION} 且库存/追溯门禁通过后方可推进 {@code IN_PROGRESS}。 */
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 追溯码（多码逗号分隔，可选）。 */
    @Schema(description = "追溯码，多码逗号分隔")
    private String traceNo;
}
