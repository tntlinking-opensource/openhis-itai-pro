/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.product;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;

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
        this.productDefinitionSelectionTerminologySupport.enrichDisplayDtos(content);
    }

    /** 应用层宽入参 → 领域采购分页 Query。 */
    private ProductDefinitionPurchasePageQuery toPurchaseQuery(ProductDefinitionPageQuery rmQuery) {
        ProductDefinitionPurchasePageQuery query = new ProductDefinitionPurchasePageQuery();
        if (rmQuery == null) {
            return query;
        }
        query.setProductTableName(rmQuery.getProductTableName());
        query.setCategoryCode(rmQuery.getCategoryCode());
        query.setActiveFlag(rmQuery.getActiveFlag());
        query.setSupplierId(rmQuery.getSupplierId());
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
        return PageRequest.of(
            effective.springPageIndex(),
            pageSize,
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
    }
}