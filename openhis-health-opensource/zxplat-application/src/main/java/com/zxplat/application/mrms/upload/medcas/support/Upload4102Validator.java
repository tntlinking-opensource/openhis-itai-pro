/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.support;

import com.zxplat.domain.share.constants.MrmsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 4102 入参校验。
 */
public final class Upload4102Validator {

    /**
     * 禁止实例化：仅静态校验方法。
     */
    private Upload4102Validator() {
    }

    /**
     * 校验 4102 三要素。
     *
     * @param psnNo 人员编号
     * @param setlId 结算 ID
     * @param stasType 0/1
     */
    public static void validate(String psnNo, String setlId, String stasType) {
        if (trimToNull(psnNo) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4102 psn_no 缺失");
        }
        if (trimToNull(setlId) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4102 setl_id 缺失");
        }
        if (!"0".equals(trimToNull(stasType)) && !"1".equals(trimToNull(stasType))) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4102 stas_type 仅支持 0/1");
        }
    }
}
