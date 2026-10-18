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
 * 住院发药（核发）响应（IF-IPP-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药响应")
public class DispenseInpatientMedicationsResponse {

    /** 领域写路径是否全部成功（固定 true；失败抛 BusinessException）。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 用户可见提示（发药成功；医保 3505 部分失败时追加警告）。 */
    @Schema(description = "提示文案")
    private String message;

    /** 本次已核发为 COMPLETED 的发放单主键列表。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
