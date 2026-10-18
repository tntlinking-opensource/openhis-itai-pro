/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.settings.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PEIS 模块参数分页检索请求（CFG-13）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchConfigRequest extends AbstractHealthPageRequest {

    /** 配置键精确过滤。 */
    private String configKey;
}
