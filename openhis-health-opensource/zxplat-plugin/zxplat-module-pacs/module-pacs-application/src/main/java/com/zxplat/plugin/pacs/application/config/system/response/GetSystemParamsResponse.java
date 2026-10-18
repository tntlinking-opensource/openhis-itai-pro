/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * PACS 系统参数快照（只读）。
 */
@Getter
@Setter
@Schema(description = "PACS 系统参数")
public class GetSystemParamsResponse {

    @Schema(description = "adapter 是否启用")
    private Boolean adapterEnabled;

    @Schema(description = "Mock 外发默认成功")
    private Boolean mockOutboundSuccess;

    @Schema(description = "说明")
    private String remark;
}
