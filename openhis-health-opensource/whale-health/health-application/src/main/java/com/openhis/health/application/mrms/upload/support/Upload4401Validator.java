/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.upload.support;

import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepagePayload;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 4401 组装结果业务校验（upload IF {@code support}）。
 *
 * <p>校验 baseinfo 必填项、医保患者 mdtrt_id/psn_no 及至少一条主诊断；失败抛
 * {@link MrmsErrorCodes#UPLOAD_ASSEMBLE_VALIDATION}。</p>
 */
public final class Upload4401Validator {

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
        if (trimToNull(base.getMedcasno()) == null) {
            errors.add("病案号缺失");
        }
        if (trimToNull(base.getPsnName()) == null) {
            errors.add("患者姓名缺失");
        }
        if (trimToNull(base.getMedfeeSumamt()) == null) {
            errors.add("住院总费用缺失");
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
            for (var row : input.getDiseinfo()) {
                if ("1".equals(trimToNull(row.getMaindiagFlag()))) {
                    hasMain = true;
                    break;
                }
            }
        }
        if (!hasMain) {
            // 缺主诊断阻断上传
            errors.add("缺少主诊断行");
        }
        if (!errors.isEmpty()) {
            // 聚合校验错误一次抛出
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    String.join("；", errors));
        }
    }
}
