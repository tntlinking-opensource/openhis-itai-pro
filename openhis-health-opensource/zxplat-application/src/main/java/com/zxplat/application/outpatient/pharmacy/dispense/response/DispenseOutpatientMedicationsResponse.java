/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊核发响应（IF-OPP-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊核发响应")
public class DispenseOutpatientMedicationsResponse {

    @Schema(description = "本地发药是否成功")
    private Boolean success;

    @Schema(description = "提示文案（可含医保失败摘要）")
    private String message;

    /** 本次核发涉及的发放单主键集合。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
