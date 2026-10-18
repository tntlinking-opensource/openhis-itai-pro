/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.support;

import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.share.constants.PriceAdjustmentDetailConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.zxplat.application.shared.product.ProductDefinitionPageQuery;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 调价明细列表项目关键字预解析（AS-FIN-PRC-002 **BR-PRC-DTL-005**）。
 *
 * <p>在 {@code item_table} 圈定范围内将项目名称/院内编码关键字解析为 {@code item_id} 集合，
 * 供 {@code PriceAdjustmentApprovedLineListQuery} 下推主表过滤；禁止在调价 QueryService 内跨聚合查目录表。</p>
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentDetailItemKeywordHelper {

    /** 产品目录分页加载（药/耗/诊疗）。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;

    /** 医疗服务读侧（历史明细关键字；新单调价不含医疗服务 Tab）。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /**
     * 解析项目关键字命中 {@code item_id} 集合。
     *
     * @param itemTable        项目物理表；空表示三档并集（药/耗/诊疗）
     * @param itemNameKeyword  项目名称模糊关键字；可空
     * @param itemCodeKeyword  院内项目编码模糊关键字；可空
     * @return 无关键字时 {@code null}（不按项目收窄）；无命中时空列表
     */
    public List<Long> resolveItemIds(String itemTable, String itemNameKeyword, String itemCodeKeyword) {
        String nameKeyword = trimToNull(itemNameKeyword);
        String codeKeyword = trimToNull(itemCodeKeyword);
        if (nameKeyword == null && codeKeyword == null) {
            // 无关键字时不按项目收窄，返回 null 供 Query 跳过 itemIds 谓词
            return null;
        }
        List<String> tables = this.resolveTargetTables(itemTable);
        Set<Long> unionIds = new LinkedHashSet<>();
        // Pre：在圈定表范围内分别解析后 UNION（BR-PRC-DTL-004/005）
        for (String table : tables) {
            Set<Long> tableIds = this.resolveIdsForTable(table, nameKeyword, codeKeyword);
            unionIds.addAll(tableIds);
        }
        if (unionIds.isEmpty()) {
            // 关键字无命中，调用方应短路空页（BR-PRC-DTL-011）
            return Collections.emptyList();
        }
        return new ArrayList<>(unionIds);
    }

    /**
     * 解析本次检索涉及的项目物理表列表。
     *
     * @param itemTable 请求传入的 item_table；空则三档并集（药/耗/诊疗）
     * @return 物理表名列表
     */
    private List<String> resolveTargetTables(String itemTable) {
        String trimmed = trimToNull(itemTable);
        if (trimmed != null) {
            // 指定 Tab 时仅查单表
            return List.of(trimmed);
        }
        List<String> tables = new ArrayList<>();
        tables.add(TableNameConstants.MED_MEDICATION_DEFINITION);
        tables.add(TableNameConstants.ADM_DEVICE_DEFINITION);
        tables.add(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        return tables;
    }

    /**
     * 单表内按名称/编码 AND 语义解析 item_id。
     *
     * @param table        项目物理表
     * @param nameKeyword  名称关键字；可空
     * @param codeKeyword  编码关键字；可空
     * @return 命中主键集合
     */
    private Set<Long> resolveIdsForTable(String table, String nameKeyword, String codeKeyword) {
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(table)) {
            // 医疗服务不走 ProductDefinitionCatalogPageLoader
            return this.resolveHealthcareServiceIds(nameKeyword, codeKeyword);
        }
        Set<Long> matched = null;
        if (nameKeyword != null) {
            matched = this.loadCatalogIdsByName(table, nameKeyword);
        }
        if (codeKeyword != null) {
            Set<Long> codeIds = this.loadCatalogIdsByCode(table, codeKeyword);
            if (matched == null) {
                matched = codeIds;
            } else {
                // 名称与编码双填时取交集（AND）
                matched.retainAll(codeIds);
            }
        }
        if (matched == null) {
            return Collections.emptySet();
        }
        return matched;
    }

    /**
     * 医疗服务表关键字解析（名称 AND 院内编码）。
     *
     * @param nameKeyword 名称关键字；可空
     * @param codeKeyword 编码关键字；可空
     * @return 命中主键集合
     */
    private Set<Long> resolveHealthcareServiceIds(String nameKeyword, String codeKeyword) {
        List<HealthcareService> services = this.healthcareServiceQueryService.listServicesForRegistration();
        Set<Long> ids = new LinkedHashSet<>();
        for (HealthcareService service : services) {
            if (service == null || service.getId() == null) {
                continue;
            }
            if (!this.matchesHealthcareServiceKeywords(service, nameKeyword, codeKeyword)) {
                // 未同时满足名称/编码条件则跳过
                continue;
            }
            ids.add(service.getId());
        }
        return ids;
    }

    /**
     * 判断医疗服务是否同时满足名称与编码关键字（双填 AND）。
     *
     * @param service     医疗服务实体
     * @param nameKeyword 名称关键字；可空
     * @param codeKeyword 编码关键字；可空
     * @return 命中时为 true
     */
    private boolean matchesHealthcareServiceKeywords(
        HealthcareService service,
        String nameKeyword,
        String codeKeyword) {
        boolean nameMatched = true;
        boolean codeMatched = true;
        if (nameKeyword != null) {
            String lowerName = nameKeyword.toLowerCase(Locale.ROOT);
            nameMatched = service.getName() != null
                && service.getName().toLowerCase(Locale.ROOT).contains(lowerName);
        }
        if (codeKeyword != null) {
            String lowerCode = codeKeyword.toLowerCase(Locale.ROOT);
            codeMatched = service.getBusNo() != null
                && service.getBusNo().toLowerCase(Locale.ROOT).contains(lowerCode);
        }
        if (nameKeyword != null && codeKeyword != null) {
            // 双关键字 AND
            return nameMatched && codeMatched;
        }
        if (nameKeyword != null) {
            return nameMatched;
        }
        return codeMatched;
    }

    /**
     * 按项目名称模糊匹配目录主键（单表）。
     *
     * @param table       项目物理表
     * @param nameKeyword 名称关键字
     * @return 命中主键集合
     */
    private Set<Long> loadCatalogIdsByName(String table, String nameKeyword) {
        Page<ProductDefinitionDisplayDto> page = this.pageCatalog(table, nameKeyword);
        Set<Long> ids = new LinkedHashSet<>();
        String lower = nameKeyword.toLowerCase(Locale.ROOT);
        // Pre：在 Loader 候选页内按 productName 二次过滤
        for (ProductDefinitionDisplayDto dto : page.getContent()) {
            if (dto == null || dto.getProductDefinitionId() == null) {
                continue;
            }
            if (dto.getProductName() != null
                && dto.getProductName().toLowerCase(Locale.ROOT).contains(lower)) {
                ids.add(dto.getProductDefinitionId());
            }
        }
        return ids;
    }

    /**
     * 按院内编码模糊匹配目录主键（单表）。
     *
     * @param table       项目物理表
     * @param codeKeyword 编码关键字
     * @return 命中主键集合
     */
    private Set<Long> loadCatalogIdsByCode(String table, String codeKeyword) {
        Page<ProductDefinitionDisplayDto> page = this.pageCatalog(table, codeKeyword);
        Set<Long> ids = new LinkedHashSet<>();
        String lower = codeKeyword.toLowerCase(Locale.ROOT);
        // Pre：在 Loader 候选页内按 productBusNo 二次过滤，保证编码语义
        for (ProductDefinitionDisplayDto dto : page.getContent()) {
            if (dto == null || dto.getProductDefinitionId() == null) {
                continue;
            }
            if (dto.getProductBusNo() != null
                && dto.getProductBusNo().toLowerCase(Locale.ROOT).contains(lower)) {
                ids.add(dto.getProductDefinitionId());
            }
        }
        return ids;
    }

    /**
     * 调用目录 Loader 拉取候选页（条件下推 DB，禁止 N+1）。
     *
     * @param table   项目物理表
     * @param keyword 检索关键字
     * @return 目录分页结果
     */
    private Page<ProductDefinitionDisplayDto> pageCatalog(String table, String keyword) {
        if (!PriceAdjustmentItemTableSupport.isSupportedItemTable(table)
            || TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(table)) {
            return Page.empty();
        }
        ProductDefinitionPageQuery pageQuery = new ProductDefinitionPageQuery();
        pageQuery.setProductTableName(table);
        pageQuery.setKeyword(keyword);
        pageQuery.setPageNo(1);
        pageQuery.setPageSize(PriceAdjustmentDetailConstants.KEYWORD_CATALOG_SCAN_PAGE_SIZE);
        return this.productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
    }
}
