/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.settings.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
