/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.support;

import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepagePayload;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 4401 组装结果业务校验（上传前）。
 *
 * <p>仅校验规范「是否必填」与医保病人必填；手术有行时校实体已映射的愈合/麻醉码。</p>
 */
public final class Upload4401Validator {

    /**
     * 禁止实例化：仅静态校验方法。
     */
    private Upload4401Validator() {
    }

    /**
     * 校验 4401 载荷可上传性。
     *
     * @param input 4401 载荷
     * @param insurancePatient 是否医保患者（决定 mdtrt_id/psn_no 必填）
     */
    public static void validate(MedcasHomepagePayload input, boolean insurancePatient) {
        List<String> errors = new ArrayList<>();
        if (input == null || input.getBaseinfo() == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4401 baseinfo 不能为空");
        }
        var base = input.getBaseinfo();
        if (trimToNull(base.getMdtrtSn()) == null) {
            errors.add("就医流水号 mdtrt_sn 缺失");
        }
        if (trimToNull(base.getMedcasno()) == null) {
            errors.add("病案号缺失");
        }
        if (trimToNull(base.getPsnName()) == null) {
            errors.add("患者姓名缺失");
        }
        if (trimToNull(base.getMedfeeSumamt()) == null) {
            errors.add("住院总费用缺失");
        }
        if (trimToNull(base.getFixmedinsCode()) == null) {
            errors.add("定点医药机构编号缺失");
        }
        if (trimToNull(base.getValiFlag()) == null) {
            errors.add("有效标志缺失");
        }
        if (insurancePatient) {
            // 医保患者强制 mdtrt_id/psn_no
            if (trimToNull(base.getMdtrtId()) == null) {
                errors.add("医保 mdtrt_id 缺失");
            }
            if (trimToNull(base.getPsnNo()) == null) {
                errors.add("医保 psn_no 缺失");
            }
        }
        boolean hasMain = false;
        if (input.getDiseinfo() != null) {
            for (MedcasHomepageDiseInfo row : input.getDiseinfo()) {
                if (trimToNull(row.getDiagCode()) == null) {
                    errors.add("诊断行缺 diag_code");
                    break;
                }
                if (trimToNull(row.getIptMedcasHmpgSn()) == null || trimToNull(row.getMdtrtSn()) == null) {
                    errors.add("诊断行缺首页流水号或就医流水号");
                    break;
                }
                if ("1".equals(trimToNull(row.getMaindiagFlag()))) {
                    hasMain = true;
                }
            }
        }
        if (!hasMain) {
            errors.add("缺少主诊断行");
        }
        // 手术有行时校愈合/麻醉等已映射必填码
        if (input.getOprninfo() != null) {
            for (MedcasHomepageOprnInfo row : input.getOprninfo()) {
                if (trimToNull(row.getOprnOprtCode()) == null) {
                    errors.add("手术行缺 oprn_oprt_code");
                    break;
                }
                if (trimToNull(row.getSincHealLvCode()) == null) {
                    errors.add("手术行缺切口愈合等级代码，请在首页手术补全");
                    break;
                }
                if (trimToNull(row.getAnstMtdCode()) == null) {
                    errors.add("手术行缺麻醉方法代码，请在首页手术补全");
                    break;
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    String.join("；", errors));
        }
    }
}
