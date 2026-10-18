/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.lis;

import com.zxplat.domain.infrastructure.integration.lis.ExtensionIn.LisAdviceApplyExtensionIn;

/**
 * 检验诊疗外发（LIS Module 提供能力）。
 */
public interface LisAdviceApplyExtension {

    /**
     * 检验外发扩展是否已装配。
     *
     * @return false 时医嘱提交不调用 LIS
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 若申请为检验类则提交 LIS 外发，否则空操作。
     *
     * @param command 就诊、申请单与明细行
     */
    default void submitIfLab(LisAdviceApplyExtensionIn command) {
    }
}
