/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 住院确认退药响应（IF-IPR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院确认退药响应")
public class ConfirmInpatientMedicineReturnResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 领域写路径是否全部成功（固定 true；失败抛 BusinessException）。 */
    @Schema(description = "是否成功，固定 true")
    private Boolean success;

    /** 成功提示文案（EX-IPR-410）。 */
    @Schema(description = "成功提示文案")
    private String message;
}
