/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas;

import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;

/**
 * 病案首页 4401 上传（health 核心 → CHS Module）。
 */
public interface MedcasHomepageUploadExtension {

    /**
     * 病案首页 4401 上传扩展是否已装配。
     *
     * @return false 时 MRMS 上传返回未装配提示
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 上传住院病案首页至医保平台（4401）。
     *
     * @param command 合同、inputJson 与操作员
     * @return 平台 infcode 与 output 摘要
     */
    default MedcasUploadExtensionOut upload(MedcasHomepageUploadExtensionIn command) {
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 module-chs-jilin-starter");
        return result;
    }
}
