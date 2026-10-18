/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.setting.domain;

import com.zxplat.framework.core.application.exception.ValidationException;

import java.util.Arrays;
import java.util.List;

/**
 * 设置提供者名称
 *
 * <p>G-全局, U-用户，R-角色，T-租户</p>
 */
public enum SettingProviderName {
    G("G", "全局"),
    U("U", "用户"),
    R("R", "角色"),
    T("T", "租户");

    private final String code;
    private final String label;

    SettingProviderName(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 优先级顺序：U > R > T > G
     */
    public static List<SettingProviderName> priorityOrder() {
        return List.of(U, R, T, G);
    }

    public static SettingProviderName of(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationException("提供者名称不能为空");
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
            .filter(x -> x.code.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ValidationException("不支持的提供者名称: " + code));
    }
}
