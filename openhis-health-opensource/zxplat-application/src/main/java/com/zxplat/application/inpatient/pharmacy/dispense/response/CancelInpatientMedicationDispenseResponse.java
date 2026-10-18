/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院作废发放响应（IF-IPP-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院作废发放响应")
public class CancelInpatientMedicationDispenseResponse {

    /** 领域写路径是否全部成功（固定 true；失败抛 BusinessException）。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 用户可见提示（作废成功或业务拒绝原因）。 */
    @Schema(description = "提示文案")
    private String message;

    /** 已作废为 DECLINED 的发放单主键列表。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
