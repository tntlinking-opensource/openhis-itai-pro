/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.contrast.service;

import com.zxplat.plugin.einvoice.boss.domain.enums.IssueKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 博思电子票统计项出站映射：院内对照表 CODE → 门诊/挂号票 CODE1。
 *
 * <p>口径对齐 areahis：门诊/挂号票 {@code chargeDetail.chargeCode} 取 {@code basic_code.code1}；
 * 住院票取 {@code basic_code.code}（YLZY*）。ZXPlat 对照表默认存住院口径 CODE，
 * 门诊/挂号出站前换算为 CODE1，与博思已挂项目一致。类名 Legacy 仅表示映射源来自 areahis 口径，
 * 本类是现行出站规则，不是新旧双轨过渡层。</p>
 */
public final class BossLegacyStatisticCode1Mapping {

    private static final Map<String, String> CODE_TO_CODE1;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("YLZY01", "YLZY01");
        map.put("YLZY02", "YLMZ01");
        map.put("YLZY03", "YLMZ02");
        map.put("YLZY04", "YLMZ03");
        map.put("YLZY05", "YLMZ04");
        map.put("YLZY06", "YLMZ05");
        map.put("YLZY07", "YLMZ10");
        map.put("YLZY08", "YLMZ06");
        map.put("YLZY09", "YLMZ07");
        map.put("YLZY10", "YLMZ08");
        map.put("YLZY11", "YLMZ09");
        map.put("YLZY12", "YLMZ10");
        map.put("YLMZ10", "YLMZ10");
        map.put("YLMZ11", "YLMZ11");
        CODE_TO_CODE1 = Collections.unmodifiableMap(map);
    }

    private BossLegacyStatisticCode1Mapping() {
    }

    /**
     * 门诊/挂号票：将对照表中的统计项 CODE 换算为老系统 CODE1；住院票原样返回。
     *
     * @param issueKind 票种（REG/AMB/IMP/COM）
     * @param bossChargeCode 对照解析得到的博思编码
     * @return 出站 chargeCode
     */
    public static String toOutboundChargeCode(String issueKind, String bossChargeCode) {
        if (bossChargeCode == null || bossChargeCode.isBlank()) {
            return bossChargeCode;
        }
        if (!needsCode1(issueKind)) {
            return bossChargeCode;
        }
        String key = bossChargeCode.trim().toUpperCase(Locale.ROOT);
        return CODE_TO_CODE1.getOrDefault(key, bossChargeCode);
    }

    /**
     * 对照缺失时的「其它」大类编码（门诊/挂号 YLMZ10，住院 YLZY12），避免 FIN/DEF 散开超行。
     *
     * @param issueKind 票种
     * @return 出站 chargeCode
     */
    public static String fallbackOtherChargeCode(String issueKind) {
        if (needsCode1(issueKind)) {
            return "YLMZ10";
        }
        return "YLZY12";
    }

    /**
     * 对照缺失时的「其它」大类名称。
     *
     * @return 收费项目名称
     */
    public static String fallbackOtherChargeName() {
        return "其它";
    }

    /**
     * 挂号、门诊票走 CODE1；住院/通用票走 CODE。
     *
     * @param issueKind 票种
     * @return 是否需要 CODE→CODE1
     */
    public static boolean needsCode1(String issueKind) {
        if (issueKind == null || issueKind.isBlank()) {
            return false;
        }
        String kind = issueKind.trim().toUpperCase(Locale.ROOT);
        return IssueKind.AMB.getCode().equals(kind) || IssueKind.REG.getCode().equals(kind);
    }
}
