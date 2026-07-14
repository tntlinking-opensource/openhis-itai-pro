/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.result;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 3301 单行上传结果 */
@Data
@Accessors(chain = true)
@Schema(description = "3301 单行结果")
public class Catalog3301UploadRowResult {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    private String productTable;

    private String fixmedinsHilistId;

    private boolean success;

    private String errorMsg;

    private String msgid;
}
