/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询药品批号维真实进价/售价请求。
 *
 * <p>{@code lotNumber} 可空：空则返回该药品全部批号价行；有值则仅返回匹配批号。</p>
 */
@Data
@Schema(description = "查询药品批号进价/售价请求")
public class ListMedicationLotPriceRequest {

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键", example = "10001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 批号过滤（可空）。
     *
     * <p>非空时仅返回该批号的进价/售价；空白则返回该药品全部批号价。</p>
     */
    @Schema(description = "批号过滤（可空）", example = "20250101")
    private String lotNumber;
}
