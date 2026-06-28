/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas;

import com.openhis.health.integration.insurance.medcas.command.MedcasHomepageUploadCommand;
import com.openhis.health.integration.insurance.medcas.result.MedcasUploadResult;

/**
 * 病案首页 4401 上传（health 核心 → CHS Module）。
 */
public interface MedcasHomepageUploadExtension {

    default boolean isEnabled() {
        return false;
    }

    default MedcasUploadResult upload(MedcasHomepageUploadCommand command) {
        MedcasUploadResult result = new MedcasUploadResult();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 chs-jilin-starter");
        return result;
    }
}
