/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 皮试药 ↔ companion「皮试检查」列表展示辅助（医生站 / 护士校对·执行共用）。
 *
 * <p>仅影响展示名与聚邻排序键；签发/执行/停嘱等写操作仍按行独立。</p>
 */
public final class SkinTestCompanionDisplaySupport {

    private SkinTestCompanionDisplaySupport() {
    }

    /**
     * 解析诊疗申请 {@code based_on} 是否锚定药品申请；是则返回皮试药主键。
     *
     * @param basedOnTable based_on 表名
     * @param basedOnId    based_on 主键
     * @return 皮试药 {@code med_medication_request.id}；非药品锚点时 null
     */
    public static Long resolveBasedOnMedicationRequestId(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            return null;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(StringUtils.trimToEmpty(basedOnTable))) {
            return basedOnId;
        }
        return null;
    }

    /**
     * 拼关联皮试药展示标签：药名 + 规格（有规格时空格拼接）。
     *
     * @param medicationName     皮试药名称；可空
     * @param specificationText  规格文本（与医嘱列表 {@code specificationText} 同源）；可空
     * @return 形如 {@code 氯化钠注射液 0.9%*250ml}；仅有名或仅有规格时返回非空段；皆空返回空串
     */
    public static String formatLinkedMedicationLabel(String medicationName, String specificationText) {
        String name = StringUtils.trimToEmpty(medicationName);
        String spec = StringUtils.trimToEmpty(specificationText);
        if (StringUtils.isEmpty(name)) {
            return spec;
        }
        if (StringUtils.isEmpty(spec)) {
            return name;
        }
        return name + " " + spec;
    }

    /**
     * 为皮试检查展示名追加关联药品，便于一眼看出「给哪味药用」。
     *
     * @param adviceDisplayName 原展示名（通常为「皮试检查」）
     * @param linkedMedicationName 关联皮试药标签（可含规格）；空则原样返回
     * @return 形如 {@code 皮试检查（关联：氯化钠注射液 0.9%*250ml）}；已含「（关联：」时不重复追加
     */
    public static String appendLinkedMedicationHint(String adviceDisplayName, String linkedMedicationName) {
        String base = StringUtils.trimToEmpty(adviceDisplayName);
        String linked = StringUtils.trimToEmpty(linkedMedicationName);
        if (StringUtils.isEmpty(linked)) {
            return base;
        }
        if (StringUtils.isEmpty(base)) {
            return "（关联：" + linked + "）";
        }
        // 已追加过则不重复拼
        if (base.contains("（关联：")) {
            return base;
        }
        return base + "（关联：" + linked + "）";
    }

    /**
     * 列表聚邻簇键：皮试药用自身 requestId；companion 用 basedOn 皮试药 id；其它行 null。
     *
     * @param medicationRequestId          药品行 requestId；非药品行传 null
     * @param basedOnMedicationRequestId   companion 锚定的皮试药 id；非 companion 传 null
     * @return 聚邻键；无关行 null
     */
    public static Long resolveSkinTestClusterKey(Long medicationRequestId, Long basedOnMedicationRequestId) {
        if (basedOnMedicationRequestId != null) {
            return basedOnMedicationRequestId;
        }
        return medicationRequestId;
    }
}
