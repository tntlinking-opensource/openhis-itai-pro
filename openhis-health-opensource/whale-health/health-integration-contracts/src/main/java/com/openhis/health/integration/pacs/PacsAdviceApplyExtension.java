/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.pacs;

import com.openhis.health.integration.pacs.command.PacsAdviceApplyCommand;

/**
 * 检查（影像）诊疗外发（PACS Module 提供能力）。
 */
public interface PacsAdviceApplyExtension {

    default boolean isEnabled() {
        return false;
    }

    /**
     * 若申请为检查类则提交 PACS/RIS 外发，否则空操作。
     */
    default void submitIfImaging(PacsAdviceApplyCommand command) {
    }
}
