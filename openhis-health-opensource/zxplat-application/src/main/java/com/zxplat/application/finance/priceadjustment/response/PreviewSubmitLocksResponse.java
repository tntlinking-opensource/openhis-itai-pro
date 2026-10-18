/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.response;

import com.zxplat.application.finance.priceadjustment.dto.LockPreviewRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 提交锁预览响应（IF-PRC-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交锁预览响应")
public class PreviewSubmitLocksResponse {

    /** 锁预览行列表。 */
    @Schema(description = "锁预览行列表")
    private List<LockPreviewRowDto> rows;
}
