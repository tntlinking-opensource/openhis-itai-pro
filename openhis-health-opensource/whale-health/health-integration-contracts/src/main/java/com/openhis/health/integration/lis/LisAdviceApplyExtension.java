/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.lis;

import com.openhis.health.integration.lis.command.LisAdviceApplyCommand;

/**
 * 检验诊疗外发（LIS Module 提供能力）。
 */
public interface LisAdviceApplyExtension {

    default boolean isEnabled() {
        return false;
    }

    /**
     * 若申请为检验类则提交 LIS 外发，否则空操作。
     */
    default void submitIfLab(LisAdviceApplyCommand command) {
    }
}
