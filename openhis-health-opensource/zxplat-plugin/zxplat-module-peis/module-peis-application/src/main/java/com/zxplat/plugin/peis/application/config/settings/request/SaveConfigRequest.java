/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.settings.request;

import lombok.Data;

/**
 * 保存 PEIS 模块参数请求（CFG-13）。
 */
@Data
public class SaveConfigRequest {

    /** 配置键（peis.* 白名单）。 */
    private String configKey;

    /** 配置值。 */
    private String configValue;
}
