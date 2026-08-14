/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.support.NationalCatalogPackHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 国家目录镜像复杂只读查询（PostgreSQL DISTINCT ON / 1301 明细）；Repository 接口号{@code @Query} */
public interface YbNationalCatalogItemLookup {

    Page<YbNationalCatalogItem> pageDistinctHilistCode(
        Long contractId,
        Long tenantId,
        String listType,
        boolean picker1312Only,
        String sourceInfno,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        boolean excludePlatformExpired,
        Pageable pageable);

    List<YbNationalCatalogItem> listDistinctForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        int limit);

    List<YbNationalCatalogItem> list1301DetailForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        String valiFlag,
        int limit);

    /**
     * 按 source_infno 查明细候选（1301 西药 / 1305 诊疗 / 1306 耗材）。
     */
    List<YbNationalCatalogItem> listDetailForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String sourceInfno,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        String valiFlag,
        int limit);

    Page<YbNationalCatalogItem> page1301Detail(
        Long contractId,
        Long tenantId,
        String listType,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        boolean excludePlatformExpired,
        Pageable pageable);

    /**
     * 按国家码批量取 1301 包装/计价单位（结算 2204/2301 限价折算）。
     *
     * <p>同码多行时优先 1301；读持久化列 {@code min_pac_*} / {@code min_prcunt}。</p>
     *
     * @param contractId  契约
     * @param tenantId    租户
     * @param hilistCodes 国家码集合
     * @return hilistCode → hint；无包装字段的码不入表
     */
    Map<String, NationalCatalogPackHint> loadPackHintsByHilistCodes(
        Long contractId,
        Long tenantId,
        Collection<String> hilistCodes);
}

