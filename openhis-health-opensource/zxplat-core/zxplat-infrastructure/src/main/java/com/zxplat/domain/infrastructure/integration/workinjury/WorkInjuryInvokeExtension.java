/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPrepareInvokeExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySubmitInvokeResultExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySubmitInvokeResultExtensionOut;

/**
 * 工伤 Win invoke 通用扩展点（prepare / submit）。
 */
public interface WorkInjuryInvokeExtension {

    default PrepareWorkInjuryInvokeExtensionOut prepareInvoke(WorkInjuryPrepareInvokeExtensionIn command) {
        throw new UnsupportedOperationException("工伤 invoke prepare 未装配");
    }

    default WorkInjurySubmitInvokeResultExtensionOut submitInvokeResult(WorkInjurySubmitInvokeResultExtensionIn command) {
        throw new UnsupportedOperationException("工伤 invoke submit 未装配");
    }
}
