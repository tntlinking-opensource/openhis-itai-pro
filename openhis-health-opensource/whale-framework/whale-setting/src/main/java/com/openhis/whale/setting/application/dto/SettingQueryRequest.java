/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设置查询请求
 *
 * @author Whale Framework
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SettingQueryRequest extends BaseQueryRequest {

    /**
     * 关键词（名称）
     */
    private String keyword;

    /**
     * 设置名称
     */
    private String name;

    /**
     * 提供者名称
     */
    private String providerName;

    /**
     * 提供者键
     */
    private String providerKey;
}
