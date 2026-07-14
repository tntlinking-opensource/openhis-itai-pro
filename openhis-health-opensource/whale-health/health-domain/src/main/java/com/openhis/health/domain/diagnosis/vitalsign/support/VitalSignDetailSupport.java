/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.support;

import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.openhis.health.domain.diagnosis.vitalsign.entity.VitalSignDetail;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 生命体征明细自然键与命令组装辅助（DM-DIA-002 v2.1）。
 */
public final class VitalSignDetailSupport {

    private VitalSignDetailSupport() {
    }

    /**
     * 归一化术语体系 code，缺省为 {@code vital_signs_code}。
     *
     * @param termCode 原始术语体系 code
     * @return 归一化后的 code
     */
    public static String normalizeTermCode(String termCode) {
        if (StringUtils.isEmpty(termCode)) {
            return TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE;
        }
        return termCode.trim();
    }

    /**
     * 构建明细自然键（术语体系 + 字典项）。
     *
     * @param termCode 术语体系 code
     * @param itemCode 字典项 code
     * @return 自然键
     */
    public static String buildNaturalKey(String termCode, String itemCode) {
        return normalizeTermCode(termCode) + VitalSignConstants.NATURAL_KEY_SEPARATOR + itemCode;
    }

    /**
     * 自命令构建明细自然键。
     *
     * @param item 明细命令
     * @return 自然键
     */
    public static String buildNaturalKey(SaveVitalSignDetailItemCommand item) {
        return buildNaturalKey(item.getTermCode(), item.getItemCode());
    }

    /**
     * 自实体构建明细自然键。
     *
     * @param detail 明细实体
     * @return 自然键
     */
    public static String buildNaturalKey(VitalSignDetail detail) {
        return buildNaturalKey(detail.getTermCode(), detail.getItemCode());
    }

    /**
     * 组装 {@code vital_signs_code} 体系下的明细命令。
     *
     * @param itemCode  字典项 code
     * @param itemValue 字典项值
     * @return 明细命令
     */
    public static SaveVitalSignDetailItemCommand vitalSignItem(String itemCode, String itemValue) {
        SaveVitalSignDetailItemCommand item = new SaveVitalSignDetailItemCommand();
        item.setTermCode(TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE);
        item.setItemCode(itemCode);
        item.setItemValue(itemValue);
        return item;
    }

    /**
     * 组装 {@code special_event_code} 体系下的明细命令。
     *
     * @param itemCode  字典项 code
     * @param itemValue 字典项值
     * @param remark    明细备注（可空）
     * @return 明细命令
     */
    public static SaveVitalSignDetailItemCommand specialEventItem(
        String itemCode,
        String itemValue,
        String remark) {
        SaveVitalSignDetailItemCommand item = new SaveVitalSignDetailItemCommand();
        item.setTermCode(TerminologyCodeSystemNameConstants.SPECIAL_EVENT_CODE);
        item.setItemCode(itemCode);
        item.setItemValue(itemValue);
        item.setRemark(remark);
        return item;
    }
}
