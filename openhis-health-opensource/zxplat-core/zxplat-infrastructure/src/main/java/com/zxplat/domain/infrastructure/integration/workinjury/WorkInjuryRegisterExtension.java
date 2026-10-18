/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjury9102ExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;

/**
 * 工伤挂号登记扩展点（9102 审批、qualification 绑定）。
 */
public interface WorkInjuryRegisterExtension {

    default boolean requiresWorkInjuryFlow(Long encounterId) {
        return false;
    }

    default PrepareWorkInjuryInvokeExtensionOut start9102(WorkInjury9102ExtensionIn command) {
        throw new UnsupportedOperationException("工伤 9102 未装配");
    }

    default void bindQualification(Long encounterId, Long contractId, String qualificationId) {
        throw new UnsupportedOperationException("工伤 qualification 绑定未装配");
    }

    default boolean assertRegistrationFeeSelfPay(Long encounterId) {
        return true;
    }
}
