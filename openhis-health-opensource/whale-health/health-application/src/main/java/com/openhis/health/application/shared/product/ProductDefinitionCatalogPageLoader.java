/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 医嘱/目录开立分页编排（读主表），替代 {@code ProductDefinitionRMQueryService#pageProductDefinition}。
 *
 * <p>职责：将 {@link ProductDefinitionPageQuery} 转为三类定义主表分页查询，合并多表结果并映射为
 * {@link ProductDefinitionDisplayDto}；术语展示列在分页映射后由
 * {@link ProductDefinitionDisplayTerminologyAssembler} 统一回填。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductDefinitionCatalogPageLoader {

    /** 药品定义主表分页读侧。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 诊疗项目定义主表分页读侧。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 耗材定义主表分页读侧。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    /** 分页结果术语展示回填。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 分页检索产品目录（药/诊/材），支持单表或多表合并视图。
     *
     * @param rmQuery 读模型风格分页入参（含表名、类目、关键字等）
     * @return 当前页 {@link ProductDefinitionDisplayDto} 与 Spring 分页元数据
     */
    public Page<ProductDefinitionDisplayDto> pageProductDefinition(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionCatalogPageQuery query = this.toCatalogQuery(rmQuery);
        Pageable pageable = this.buildPageable(rmQuery);
        List<String> tables = this.resolveTables(query);
        // 单表路径：条件下推至对应定义 QueryService，避免无意义 merge。
        if (tables.size() == 1) {
            return this.pageSingleTable(tables.get(0), query, pageable);
        }
        // 多表路径：各表仍 DB 分页（perTablePageable），合并后截取本页上限（目录「全部」Tab 口径；非全量内存分页）。
        // DOC-CONFLICT：total 为各表 total 之和，与「全局精确 total」存在偏差，与历史 RM 行为一致。
        int perTable = Math.max(1, pageable.getPageSize() / tables.size());
        Pageable perTablePageable = PageRequest.of(pageable.getPageNumber(), perTable, pageable.getSort());
        List<ProductDefinitionDisplayDto> merged = new ArrayList<>();
        long total = 0L;
        for (String table : tables) {
            Page<ProductDefinitionDisplayDto> slice = this.pageSingleTable(table, query, perTablePageable);
            merged.addAll(slice.getContent());
            total += slice.getTotalElements();
        }
        int end = Math.min(merged.size(), pageable.getPageSize());
        List<ProductDefinitionDisplayDto> pageRows;
        if (end > 0) {
            pageRows = merged.subList(0, end);
        } else {
            pageRows = List.of();
        }
        Page<ProductDefinitionDisplayDto> result = new PageImpl<>(pageRows, pageable, total);
        this.enrichPageContent(result.getContent());
        return result;
    }

    /**
     * 按物理表名路由至对应定义 QueryService 分页，并映射为展示 DTO。
     *
     * @param table    产品定义表物理名
     * @param query    目录分页业务条件
     * @param pageable Spring 分页与排序
     * @return 单表分页结果；未知表名时返回空页
     */
    private Page<ProductDefinitionDisplayDto> pageSingleTable(
        String table,
        ProductDefinitionCatalogPageQuery query,
        Pageable pageable
    ) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
            return this.mapMedicationPage(medicationDefinitionQueryService.pageForCatalog(query, pageable));
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return this.mapDevicePage(deviceDefinitionQueryService.pageForCatalog(query, pageable));
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            return this.mapActivityPage(activityDefinitionQueryService.pageForCatalog(query, pageable));
        }
        return Page.empty(pageable);
    }

    /**
     * 药品定义分页实体 → 展示 DTO 分页壳。
     *
     * @param page 药品定义 Spring 分页
     * @return 展示 DTO 分页（保留 pageable 与 totalElements）
     */
    private Page<ProductDefinitionDisplayDto> mapMedicationPage(Page<MedicationDefinition> page) {
        List<ProductDefinitionDisplayDto> rows = new ArrayList<>();
        // Loop：逐行映射药品定义，跳过 null 或缺主键行。
        for (MedicationDefinition med : page.getContent()) {
            ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromMedication(med);
            if (dto != null) {
                rows.add(dto);
            }
        }
        Page<ProductDefinitionDisplayDto> result = new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
        this.enrichPageContent(result.getContent());
        return result;
    }

    /**
     * 器材定义分页实体 → 展示 DTO 分页壳。
     *
     * @param page 器材定义 Spring 分页
     * @return 展示 DTO 分页
     */
    private Page<ProductDefinitionDisplayDto> mapDevicePage(Page<DeviceDefinition> page) {
        List<ProductDefinitionDisplayDto> rows = new ArrayList<>();
        for (DeviceDefinition dev : page.getContent()) {
            ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromDevice(dev);
            if (dto != null) {
                rows.add(dto);
            }
        }
        Page<ProductDefinitionDisplayDto> result = new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
        this.enrichPageContent(result.getContent());
        return result;
    }

    /**
     * 活动定义分页实体 → 展示 DTO 分页壳。
     *
     * @param page 活动定义 Spring 分页
     * @return 展示 DTO 分页
     */
    private Page<ProductDefinitionDisplayDto> mapActivityPage(Page<ActivityDefinition> page) {
        List<ProductDefinitionDisplayDto> rows = new ArrayList<>();
        for (ActivityDefinition act : page.getContent()) {
            ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromActivity(act);
            if (dto != null) {
                rows.add(dto);
            }
        }
        Page<ProductDefinitionDisplayDto> result = new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
        this.enrichPageContent(result.getContent());
        return result;
    }

    /** 批量回填字典展示列（B.10.8.1）。 */
    private void enrichPageContent(List<ProductDefinitionDisplayDto> content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        this.productDefinitionDisplayTerminologyAssembler.enrichDisplayDtos(content);
    }

    /**
     * 解析本次分页应查询的物理表列表（显式表名 / 默认真源三表 / 划价排除药品）。
     *
     * @param query 目录分页条件
     * @return 非空表名列表，顺序影响多表 merge 时的行序
     */
    private List<String> resolveTables(ProductDefinitionCatalogPageQuery query) {
        List<String> tables = new ArrayList<>();
        if (query != null && query.getProductTableNames() != null) {
            for (String name : query.getProductTableNames()) {
                if (StringUtils.isNotEmpty(name)) {
                    tables.add(name.trim());
                }
            }
        }
        if (tables.isEmpty() && query != null && StringUtils.isNotEmpty(query.getProductTableName())) {
            tables.add(query.getProductTableName().trim());
        }
        // 未指定表名时默认检索药/诊/材三表（医生站目录「全部」）。
        if (tables.isEmpty()) {
            tables.add(TableNameConstants.MED_MEDICATION_DEFINITION);
            tables.add(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            tables.add(TableNameConstants.ADM_DEVICE_DEFINITION);
        }
        Integer pricingFlag = null;
        if (query != null) {
            pricingFlag = query.getPricingFlag();
        }
        // 划价场景排除药品表：仅检索诊疗与耗材定义。
        if (pricingFlag != null && pricingFlag.equals(GeneralSharedConstants.PRODUCT_DEFINITION_PRICING_FLAG_EXCLUDE_MEDICATION)) {
            tables.remove(TableNameConstants.MED_MEDICATION_DEFINITION);
        }
        return tables;
    }

    /**
     * 读模型分页入参 → 领域目录分页 Query（字段一一对齐）。
     *
     * @param rmQuery 读模型风格入参；允许 null
     * @return 领域查询 DTO，永不为 null
     */
    private ProductDefinitionCatalogPageQuery toCatalogQuery(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionCatalogPageQuery query = new ProductDefinitionCatalogPageQuery();
        if (rmQuery == null) {
            return query;
        }
        query.setProductTableName(rmQuery.getProductTableName());
        query.setProductTableNames(rmQuery.getProductTableNames());
        query.setCategoryCode(rmQuery.getCategoryCode());
        query.setExcludeMedicationCategoryCodes(rmQuery.getExcludeMedicationCategoryCodes());
        query.setActiveFlag(rmQuery.getActiveFlag());
        query.setSupplierId(rmQuery.getSupplierId());
        query.setManufacturer(rmQuery.getManufacturer());
        query.setPricingFlag(rmQuery.getPricingFlag());
        query.setKeyword(rmQuery.getKeyword());
        query.setPrepickedProductKeys(rmQuery.getPrepickedProductKeys());
        query.setPageNo(rmQuery.getPageNo());
        query.setPageSize(rmQuery.getPageSize());
        query.setSortField(rmQuery.getSortField());
        query.setSortOrder(rmQuery.getSortOrder());
        return query;
    }

    /**
     * 由 {@link AbstractPageQuery} 分页键构造 Spring {@link Pageable}（B.10.11：{@code springPageIndex} + 有界 pageSize）。
     *
     * @param rmQuery 读模型分页入参；{@code null} 时按第 1 页、默认页大小
     * @return 默认按创建时间倒序的 Pageable
     */
    private Pageable buildPageable(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionPageQuery effective = rmQuery;
        if (effective == null) {
            effective = new ProductDefinitionPageQuery();
        }
        int pageSize = effective.getBoundedPageSize();
        if (pageSize > GeneralSharedConstants.PRODUCT_DEFINITION_RM_PAGE_SIZE_MAX) {
            pageSize = GeneralSharedConstants.PRODUCT_DEFINITION_RM_PAGE_SIZE_MAX;
        }
        return PageRequest.of(
            effective.springPageIndex(),
            pageSize,
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
    }
}
