/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 3302 单行撤销结果 */
@Data
@Accessors(chain = true)
@Schema(description = "3302 单行结果")
public class Catalog3302RevokeRowResult {

    private Long productId;
    private String productTable;
    private String fixmedinsHilistId;
    private boolean success;
    private String errorMsg;
}
