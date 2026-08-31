/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
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
 * 采购/进货选品分页编排：按表名路由药品/耗材主表查询，替代 {@code ProductDefinitionRMQueryService#pageProductForPurchase}。
 */
@Component
@RequiredArgsConstructor
public class ProductDefinitionPurchasePageLoader {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 分页查询采购选品（展示 DTO），入参仍兼容读模型 {@link ProductDefinitionPageQuery}。
     */
    public Page<ProductDefinitionDisplayDto> pageForPurchase(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionPurchasePageQuery query = this.toPurchaseQuery(rmQuery);
        if (query.getActiveFlag() == null) {
            query.setActiveFlag(Boolean.TRUE);
        }
        Pageable pageable = this.buildPageable(rmQuery);
        String table = trimToNull(query.getProductTableName());
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
            return this.mapMedicationPage(medicationDefinitionQueryService.pageForPurchase(query, pageable));
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return this.mapDevicePage(deviceDefinitionQueryService.pageForPurchase(query, pageable));
        }
        int half = Math.max(1, pageable.getPageSize() / 2);
        Pageable halfPage = PageRequest.of(pageable.getPageNumber(), half, pageable.getSort());
        Page<MedicationDefinition> medPage = medicationDefinitionQueryService.pageForPurchase(query, halfPage);
        Page<DeviceDefinition> devPage = deviceDefinitionQueryService.pageForPurchase(query, halfPage);
        List<ProductDefinitionDisplayDto> content = new ArrayList<>();
        for (MedicationDefinition med : medPage.getContent()) {
            ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromMedication(med);
            if (dto != null) {
                content.add(dto);
            }
        }
        for (DeviceDefinition dev : devPage.getContent()) {
            ProductDefinitionDisplayDto dto = ProductDefinitionDisplaySupport.fromDevice(dev);
            if (dto != null) {
                content.add(dto);
            }
        }
        long total = medPage.getTotalElements() + devPage.getTotalElements();
        Page<ProductDefinitionDisplayDto> mergedPage = new PageImpl<>(content, pageable, total);
        this.enrichPageContent(mergedPage.getContent());
        return mergedPage;
    }

    /** 药品采购分页 → 展示 DTO 并术语回填。 */
    private Page<ProductDefinitionDisplayDto> mapMedicationPage(Page<MedicationDefinition> page) {
        List<ProductDefinitionDisplayDto> rows = new ArrayList<>();
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

    /** 耗材采购分页 → 展示 DTO 并术语回填。 */
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

    /** 批量回填字典展示列（B.10.8.1）。 */
    private void enrichPageContent(List<ProductDefinitionDisplayDto> content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        this.productDefinitionDisplayTerminologyAssembler.enrichDisplayDtos(content);
    }

    /**
     * 应用层宽入参 → 领域采购分页 Query（含 manufacturerId / manufacturerIds 下推）。
     *
     * @param rmQuery 应用层选品分页入参；null 时返回空条件对象
     * @return 领域采购分页查询；永不为 null
     */
    private ProductDefinitionPurchasePageQuery toPurchaseQuery(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionPurchasePageQuery query = new ProductDefinitionPurchasePageQuery();
        if (rmQuery == null) {
            return query;
        }
        query.setProductTableName(rmQuery.getProductTableName());
        query.setCategoryCode(rmQuery.getCategoryCode());
        query.setActiveFlag(rmQuery.getActiveFlag());
        query.setSupplierId(rmQuery.getSupplierId());
        query.setManufacturerId(rmQuery.getManufacturerId());
        query.setManufacturerIds(rmQuery.getManufacturerIds());
        query.setManufacturer(rmQuery.getManufacturer());
        query.setKeyword(rmQuery.getKeyword());
        query.setPrepickedProductKeys(rmQuery.getPrepickedProductKeys());
        query.setPageNo(rmQuery.getPageNo());
        query.setPageSize(rmQuery.getPageSize());
        query.setSortField(rmQuery.getSortField());
        query.setSortOrder(rmQuery.getSortOrder());
        return query;
    }

    /**
     * 由 {@link ProductDefinitionPageQuery} 构造 Spring {@link Pageable}（B.10.11，禁止手写 pageNo-1）。
     *
     * @param rmQuery 读模型分页入参
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
        // B.2.1：关键字非空时勿带 Sort，避免覆盖 Spec 内「精确优先」ORDER BY
        if (StringUtils.isNotEmpty(effective.getKeyword())) {
            return PageRequest.of(effective.springPageIndex(), pageSize);
        }
        return PageRequest.of(
            effective.springPageIndex(),
            pageSize,
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
    }
}
