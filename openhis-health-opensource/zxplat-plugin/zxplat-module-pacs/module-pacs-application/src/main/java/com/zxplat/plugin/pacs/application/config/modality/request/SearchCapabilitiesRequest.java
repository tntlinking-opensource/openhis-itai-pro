/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.modality.request;

import com.zxplat.framework.core.application.paging.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模态能力列表请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模态能力-列表请求")
public class SearchCapabilitiesRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模态码")
    private String modalityCode;

    @Schema(description = "仅启用")
    private Boolean enabledOnly;
}
