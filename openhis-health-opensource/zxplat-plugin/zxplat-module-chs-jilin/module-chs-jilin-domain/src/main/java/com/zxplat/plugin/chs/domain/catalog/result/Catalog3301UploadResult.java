/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 3301 批量上传结果（按行） */
@Data
@Accessors(chain = true)
@Schema(description = "3301 上传结果")
public class Catalog3301UploadResult {

    @Schema(description = "是否全部成功")
    private boolean allSuccess;

    @Schema(description = "成功行数")
    private int successCount;

    @Schema(description = "失败行数")
    private int failCount;

    @Schema(description = "汇总提示")
    private String summary;

    @Schema(description = "各行结果")
    private List<Catalog3301UploadRowResult> rows;
}
