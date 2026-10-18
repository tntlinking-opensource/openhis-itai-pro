/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 维护端保存组套行响应（IF-BAS-OTL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护端保存组套行响应")
public class SaveMaintainedOrderTemplateLinesResponse {

    @Schema(description = "有效行数")
    /** 有效行数。 */
    private Integer savedLineCount;
}
