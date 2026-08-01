/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.share.enums.HiFeesetlType;

/**
 * 3505 {@code hi_feesetl_type}：参保地 vs 就医地；区划不全时默认本地（产品口径 A）。
 */
public final class InventoryUploadHiFeesetlAssembler {

    private InventoryUploadHiFeesetlAssembler() {
    }

    /**
     * 自费结算固定 {@link HiFeesetlType#SELF}。
     *
     * @return {@code 0}
     */
    public static String resolveSelfPay() {
        return HiFeesetlType.SELF.getCode();
    }

    /**
     * 医保结算：对比参保地区划与就医地区划。
     *
     * <p>同市 → 本地；否则 → 异地；任一侧区划不足 4 位数字 → 默认本地。</p>
     *
     * @param insuplcAdmdvs 参保地医保区划
     * @param mdtrtareaAdmdvs 就医地医保区划
     * @return {@code 1} 本地或 {@code 2} 异地
     */
    public static String resolveInsurance(String insuplcAdmdvs, String mdtrtareaAdmdvs) {
        String insu = digitsOnly(insuplcAdmdvs);
        String mdtrt = digitsOnly(mdtrtareaAdmdvs);
        // 区划不全：判不出 → 默认本地
        if (insu.length() < 4 || mdtrt.length() < 4) {
            return HiFeesetlType.LOCAL.getCode();
        }
        String insuCity = insu.substring(0, 4);
        String mdtrtCity = mdtrt.substring(0, 4);
        // 同市：本地
        if (insuCity.equals(mdtrtCity)) {
            return HiFeesetlType.LOCAL.getCode();
        }
        // 省内异地或跨省：平台均报异地
        return HiFeesetlType.REMOTE.getCode();
    }

    private static String digitsOnly(String raw) {
        // 空串：无法截取市码
        if (isEmpty(raw)) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }
}
