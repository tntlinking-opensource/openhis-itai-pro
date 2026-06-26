/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 3302 单行撤销结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "3302 单行结果")
public class Catalog3302RevokeRowResultDto {

    private Long productId;
    private String productTable;
    private String fixmedinsHilistId;
    private boolean success;
    private String errorMsg;
}
