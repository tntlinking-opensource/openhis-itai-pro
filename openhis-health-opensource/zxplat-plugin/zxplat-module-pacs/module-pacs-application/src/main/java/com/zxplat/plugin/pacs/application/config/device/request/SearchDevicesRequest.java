/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.request;

import com.zxplat.framework.core.application.paging.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备台账列表请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备台账-列表请求")
public class SearchDevicesRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 模态码。 */
    @Schema(description = "模态码")
    private String modalityCode;

    /** 仅启用。 */
    @Schema(description = "仅启用")
    private Boolean enabledOnly;

    /** 关键字（匹配名称或编码）。 */
    @Schema(description = "关键字（名称或编码）")
    private String keyword;
}
