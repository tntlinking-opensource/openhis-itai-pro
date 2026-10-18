/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.repository;

import com.zxplat.plugin.chs.domain.catalog.result.HospitalCatalogPresence;
import com.zxplat.plugin.chs.domain.share.enums.ListType;

import java.util.Collection;
import java.util.Map;

/**
 * 按院内 bus_no + list_type 解析药品/诊疗/耗材存在性（含停用）。
 */
public interface HospitalCatalogPresenceLookup {

    /**
     * 批量解析。key = {@code listType + '|' + busNo}。
     *
     * @param listType 目录类别（决定查哪张定义表）
     * @param busNos   院内编码集合
     * @return 命中映射；未命中不入 map
     */
    Map<String, HospitalCatalogPresence> resolve(String listType, Collection<String> busNos);

    /**
     * 组装 map key。
     *
     * @param listType 目录类别
     * @param busNo    院内编码
     * @return 非空 key
     */
    static String key(String listType, String busNo) {
        String lt = listType == null ? "" : listType.trim();
        String bn = busNo == null ? "" : busNo.trim();
        return lt + "|" + bn;
    }

    /**
     * list_type → 产品表。
     *
     * @param listType 目录类别
     * @return 表名；未知时按药品
     */
    static String productTableOf(String listType) {
        if (ListType.MEDICAL_SERVICE.getCode().equals(listType)) {
            return "wkf_activity_definition";
        }
        if (ListType.MEDICAL_CONSUMABLE.getCode().equals(listType)) {
            return "adm_device_definition";
        }
        return "med_medication_definition";
    }
}
