/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.support;

import com.zxplat.application.outpatient.financial.registration.dto.YbRegisterBenefitAreaHintDto;
import com.zxplat.framework.core.application.util.StringUtils;

/** 参保地 vs 就医地区划对比。 */
public final class RegisterBenefitAreaHintHelper {

    public static final String LOCAL = "LOCAL";
    public static final String IN_PROVINCE = "IN_PROVINCE";
    public static final String CROSS_PROVINCE = "CROSS_PROVINCE";
    public static final String UNKNOWN = "UNKNOWN";

    private RegisterBenefitAreaHintHelper() {
    }

    public static YbRegisterBenefitAreaHintDto build(String insuplcAdmdvs, String mdtrtareaAdmdvs) {
        YbRegisterBenefitAreaHintDto hint = new YbRegisterBenefitAreaHintDto();
        hint.setInsuplcAdmdvs(StringUtils.trimToNull(insuplcAdmdvs));
        hint.setMdtrtareaAdmdvs(StringUtils.trimToNull(mdtrtareaAdmdvs));
        String insu = normalizeAdmdvs(hint.getInsuplcAdmdvs());
        String mdtrt = normalizeAdmdvs(hint.getMdtrtareaAdmdvs());
        if (insu.length() < 4 || mdtrt.length() < 4) {
            hint.setAreaKind(UNKNOWN);
            hint.setHintMessage("参保地或就医地区划不完整，无法判断异地类型");
            return hint;
        }
        String insuProvince = insu.substring(0, 2);
        String mdtrtProvince = mdtrt.substring(0, 2);
        if (!insuProvince.equals(mdtrtProvince)) {
            hint.setAreaKind(CROSS_PROVINCE);
            hint.setHintMessage("跨省异地就医：参保地 "
                + hint.getInsuplcAdmdvs()
                + "，就医地 "
                + hint.getMdtrtareaAdmdvs()
                + "；请核对结算与对账规则");
            return hint;
        }
        String insuCity = insu.substring(0, 4);
        String mdtrtCity = mdtrt.substring(0, 4);
        if (insuCity.equals(mdtrtCity)) {
            hint.setAreaKind(LOCAL);
            hint.setHintMessage("本地参保本地就医");
            return hint;
        }
        hint.setAreaKind(IN_PROVINCE);
        hint.setHintMessage("省内异地就医：参保地 "
            + hint.getInsuplcAdmdvs()
            + "，就医地 "
            + hint.getMdtrtareaAdmdvs()
            + "；请核对结算与对账规则");
        return hint;
    }

    /**
     * 医保区划码归一：去非数字字符；空输入返回空串。
     *
     * @param admdvs 原始区划码
     * @return 归一化结果
     */
    private static String normalizeAdmdvs(String admdvs) {
        if (StringUtils.isEmpty(admdvs)) {
            return "";
        }
        return admdvs.replaceAll("\\D", "");
    }
}
