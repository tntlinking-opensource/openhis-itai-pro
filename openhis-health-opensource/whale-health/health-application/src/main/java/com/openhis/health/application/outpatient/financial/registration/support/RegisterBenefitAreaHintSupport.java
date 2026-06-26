/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.application.outpatient.financial.registration.dto.YbRegisterBenefitAreaHintDto;
import org.apache.commons.lang3.StringUtils;

/** 参保地 vs 就医地区划对比。 */
public final class RegisterBenefitAreaHintSupport {

    public static final String LOCAL = "LOCAL";
    public static final String IN_PROVINCE = "IN_PROVINCE";
    public static final String CROSS_PROVINCE = "CROSS_PROVINCE";
    public static final String UNKNOWN = "UNKNOWN";

    private RegisterBenefitAreaHintSupport() {
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

    private static String normalizeAdmdvs(String admdvs) {
        if (StringUtils.isBlank(admdvs)) {
            return "";
        }
        return admdvs.replaceAll("\\D", "");
    }
}
