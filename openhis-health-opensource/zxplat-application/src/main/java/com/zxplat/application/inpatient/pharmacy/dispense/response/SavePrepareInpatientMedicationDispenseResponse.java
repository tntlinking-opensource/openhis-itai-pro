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
 * 住院保存配药响应（IF-IPP-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院保存配药响应")
public class SavePrepareInpatientMedicationDispenseResponse {

    /** 领域写路径是否全部成功（固定 true；失败抛 BusinessException）。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 用户可见提示（成功或部分医保上传失败追加警告）。 */
    @Schema(description = "提示文案")
    private String message;

    /** 本次请求涉及的发放单主键列表。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
