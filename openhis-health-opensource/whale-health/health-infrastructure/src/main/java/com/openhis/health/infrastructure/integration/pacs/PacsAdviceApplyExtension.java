/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.pacs;

import com.openhis.health.infrastructure.integration.pacs.ExtensionIn.PacsAdviceApplyExtensionIn;

/**
 * 检查（影像）诊疗外发（PACS Module 提供能力）。
 */
public interface PacsAdviceApplyExtension {

    /**
     * 检查外发扩展是否已装配。
     *
     * @return false 时医嘱提交不调用 PACS/RIS
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 若申请为检查类则提交 PACS/RIS 外发，否则空操作。
     *
     * @param command 就诊、申请单与明细行
     */
    default void submitIfImaging(PacsAdviceApplyExtensionIn command) {
    }
}
