/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 删除药房调入单响应（IF-PHA-TI-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除药房调入单 IF-PHA-TI-10")
public class RemoveTransferInResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
