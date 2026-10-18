/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;

/**
 * 工伤审批材料上传（2301）扩展点。
 */
public interface WorkInjuryApprovalUploadExtension {

    default boolean supportsApprovalUpload(Long encounterId) {
        return false;
    }

    default PrepareWorkInjuryInvokeExtensionOut upload2301(Long encounterId, Long contractId, Long operatorId) {
        throw new UnsupportedOperationException("工伤 2301 上传未装配");
    }
}
