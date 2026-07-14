/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

/**
 * Whale 可选扩展模块元数据（启动日志与运维可观测）。
 */
public interface WhaleModuleDescriptor {

    String moduleId();

    String displayName();

    /** 如 {@code openhis.modules.emr.enabled} */
    String enabledProperty();

    /** 依赖的其他 moduleId，可为空数组 */
    String[] dependsOnModuleIds();

    /** 模块启用的业务能力键（供权限/Feature 双层开关与启动日志）。 */
    default String[] featureKeys() {
        return new String[0];
    }
}
