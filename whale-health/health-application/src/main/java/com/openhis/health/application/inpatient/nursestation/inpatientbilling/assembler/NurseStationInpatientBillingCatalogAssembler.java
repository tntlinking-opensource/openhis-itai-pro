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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPackageChildDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-PRC-01：护士划价选品目录装配（BR-IP-PRC-017；不做库存 Gate）。
 *
 * <p>读侧批量拉取 ChargeItemDefinition、套餐子项；写侧子项定价由 {@link #applyChildActivityPricing} 复用。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingCatalogAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 装配划价选品当前页行。
     *
     * @param request 请求（含定价来源）
     * @param page    产品定义分页
     * @return 目录行
     */
    public List<NurseStationPricingCatalogRowDto> assembleCatalogRows(
        PageNurseStationPricingCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page) {
        if (request == null || page == null) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String pricingSource = StringUtils.defaultIfBlank(
            request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        if (!this.isAllowedPricingSource(pricingSource)) {
            return List.of();
        }
        Map<String, List<ChargeItemDefinition>> priceByKey = this.loadPricingGrouped(content);
        Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId =
            this.loadPackageChildrenForPage(content);
        List<NurseStationPricingCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            NurseStationPricingCatalogRowDto dto = this.baseRow(productRow);
            dto.setPricingSourceCode(pricingSource);
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(compositeKey, List.of());
            ChargeItemDefinition selected =
                InpatientDoctorStationAdviceCatalogAssembler.selectCatalogPriceLine(
                    pricingSource, lines, productRow.getProductTableName());
            if (selected != null) {
                dto.setChargeItemDefinitionId(selected.getId());
                dto.setUnitPrice(selected.getPriceValue());
            } else if (productRow.getChargeItemDefinitionId() != null) {
                dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productRow.getProductTableName())) {
                dto.setActivityId(productRow.getProductDefinitionId());
                List<ActivityDefinitionChildQueryModel> childModels =
                    childrenByActivityId.get(productRow.getProductDefinitionId());
                if (childModels != null && !childModels.isEmpty()) {
                    dto.setPackageChildren(this.mapPackageChildren(childModels));
                }
                String gateKind = this.resolveDurationGateKind(productRow.getProductName());
                dto.setDurationGateKind(gateKind);
                dto.setOxygenOrEcgFlag(gateKind != null);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                dto.setDeviceDefId(productRow.getProductDefinitionId());
            }
            String selectedPriceTypeCode = null;
            if (selected != null) {
                selectedPriceTypeCode = selected.getPriceTypeCode();
            }
            dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow,
                ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
                    productRow, selectedPriceTypeCode),
                dto.getUnitPrice()));
            rows.add(dto);
        }
        return rows;
    }

    private boolean isAllowedPricingSource(String pricingSource) {
        if (DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE.equals(pricingSource)) {
            return true;
        }
        if (DoctorStationAdviceConstants.PRICING_SOURCE_BATCH_SELLING_PRICE.equals(pricingSource)) {
            return true;
        }
        return false;
    }

    private NurseStationPricingCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        NurseStationPricingCatalogRowDto dto = new NurseStationPricingCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        dto.setName(productRow.getProductName());
        dto.setPyCode(productRow.getPyStr());
        dto.setWbCode(productRow.getWbStr());
        dto.setCategoryCode(productRow.getCategoryCode());
        dto.setCategoryCodeDictText(productRow.getCategoryCodeDictText());
        dto.setUnitCode(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(productRow));
        dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(productRow));
        return dto;
    }

    private Map<String, List<ChargeItemDefinition>> loadPricingGrouped(List<ProductDefinitionDisplayDto> content) {
        Map<String, List<Long>> idsByTable = new LinkedHashMap<>();
        for (ProductDefinitionDisplayDto row : content) {
            if (row == null || row.getProductTableName() == null || row.getProductDefinitionId() == null) {
                continue;
            }
            idsByTable.computeIfAbsent(row.getProductTableName(), k -> new ArrayList<>())
                .add(row.getProductDefinitionId());
        }
        Map<String, List<ChargeItemDefinition>> grouped = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : idsByTable.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
            cmd.setInstanceTable(entry.getKey());
            cmd.setInstanceIds(entry.getValue());
            List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
            for (ChargeItemDefinition line : lines) {
                if (line == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    line.getInstanceTable(), line.getInstanceId());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
            }
        }
        return grouped;
    }

    public void applyChildActivityPricing(
        NurseStationPricingWriteItemDto childRow,
        Long childActivityId,
        String pricingSourceCode) {
        if (childRow == null || childActivityId == null) {
            return;
        }
        String pricingSource = StringUtils.defaultIfBlank(
            pricingSourceCode, DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        cmd.setInstanceIds(List.of(childActivityId));
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
        ChargeItemDefinition selected = InpatientDoctorStationAdviceCatalogAssembler.selectCatalogPriceLine(
            pricingSource, lines, TableNameConstants.WKF_ACTIVITY_DEFINITION);
        if (selected != null) {
            childRow.setChargeItemDefinitionId(selected.getId());
            childRow.setUnitPrice(selected.getPriceValue());
            if (childRow.getQuantity() != null) {
                childRow.setTotalPrice(selected.getPriceValue().multiply(childRow.getQuantity()));
            }
        }
        childRow.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        childRow.setProductDefinitionId(childActivityId);
        childRow.setActivityId(childActivityId);
    }

    private Map<Long, List<ActivityDefinitionChildQueryModel>> loadPackageChildrenForPage(
        List<ProductDefinitionDisplayDto> content) {
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto row : content) {
            if (row != null && TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())
                && row.getProductDefinitionId() != null) {
                activityIds.add(row.getProductDefinitionId());
            }
        }
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        return activityDefinitionQueryService.listChildrenByActivityIds(new ArrayList<>(activityIds));
    }

    private List<NurseStationPricingPackageChildDto> mapPackageChildren(
        List<ActivityDefinitionChildQueryModel> childModels) {
        List<NurseStationPricingPackageChildDto> list = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : childModels) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            NurseStationPricingPackageChildDto dto = new NurseStationPricingPackageChildDto();
            dto.setChildrenId(child.getChildrenId());
            dto.setChildrenNum(child.getChildrenNum());
            dto.setChildrenName(child.getChildrenName());
            list.add(dto);
        }
        return list;
    }

    public String resolveDurationGateKind(String productName) {
        String name = StringUtils.defaultString(productName);
        if (name.contains("吸氧") || name.contains("氧气")) {
            return InpatientNurseStationConstants.DURATION_GATE_KIND_OXYGEN;
        }
        if (name.contains("心电")) {
            return InpatientNurseStationConstants.DURATION_GATE_KIND_ECG;
        }
        return null;
    }
}
