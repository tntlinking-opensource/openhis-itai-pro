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

package com.openhis.health.application.outpatient.doctorstation.advice.query;

import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceOrderBindRowDto;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateRangeCode;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * IF-DOR-12：门诊医嘱绑定行只读装载。
 *
 * <p>与住院 {@code InpatientDoctorStationAdviceOrderBindQueryService} 同构：用法来源键为
 * {@code method_code} 术语 code；诊疗来源键为活动定义主键字符串。经
 * {@link ServiceDeviceTemplateQueryService} 查询全院启用态模板，批量解析展示名（A.9.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class DoctorStationAdviceOrderBindQueryService {

    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按绑定类型与主数据主键装载绑定行（诊疗来源等 {@code item_id} 存数字主键字符串的场景）。
     *
     * @param bindType           绑定类型业务码
     * @param itemId             诊疗活动定义主键
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合
     */
    public List<DoctorStationAdviceOrderBindRowDto> listBindings(
        String bindType,
        Long itemId,
        Long organizationUnitId
    ) {
        if (bindType == null || itemId == null) {
            return new ArrayList<>();
        }
        return this.listBindingsBySourceItemKey(bindType, String.valueOf(itemId), organizationUnitId);
    }

    /**
     * 按绑定类型与来源项键装载绑定行。
     *
     * <p>用法绑定：{@code sourceItemKey} 为 {@code method_code}（术语 code）；诊疗绑定：
     * {@code sourceItemKey} 为 {@code wkf_activity_definition.id} 字符串。</p>
     *
     * @param bindType           绑定类型业务码
     * @param sourceItemKey      模板来源项键
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合；入参缺失或未知绑定类型时返回可变空列表
     */
    public List<DoctorStationAdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId
    ) {
        List<DoctorStationAdviceOrderBindRowDto> rows = new ArrayList<>();
        if (bindType == null || StringUtils.isBlank(sourceItemKey)) {
            return rows;
        }
        Optional<BindQueryContext> contextOpt = resolveBindContext(bindType);
        if (contextOpt.isEmpty()) {
            return rows;
        }
        BindQueryContext context = contextOpt.get();
        String rangeCode = ServiceDeviceTemplateRangeCode.HOSPITAL.getCode();
        String itemIdKey = sourceItemKey.trim();
        List<ServiceDeviceTemplateQueryModel> bindings = serviceDeviceTemplateQueryService.listBindingsByRangeTypeAndSource(
            rangeCode,
            context.typeCode(),
            context.itemTable(),
            itemIdKey
        );
        if (bindings.isEmpty()) {
            return rows;
        }
        int maxRows = DoctorStationAdviceConstants.ORDER_BIND_LIST_MAX_ROWS;
        int bound = bindings.size();
        if (bound > maxRows) {
            bound = maxRows;
        }
        Map<String, String> methodLabelByCode = new HashMap<>();
        if (context.sourceIsMethodCode()) {
            methodLabelByCode = terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        }
        String sourceItemName = resolveSourceItemName(context, itemIdKey, methodLabelByCode);
        Map<String, ProductDefinitionDisplayDto> targetProductByKey = batchLoadTargetProducts(bindings);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        int priority = 1;
        for (int i = 0; i < bound; i++) {
            ServiceDeviceTemplateQueryModel binding = bindings.get(i);
            DoctorStationAdviceOrderBindRowDto row = new DoctorStationAdviceOrderBindRowDto();
            row.setBindType(bindType);
            row.setSourceItemId(resolveSourceItemIdForDisplay(context, itemIdKey));
            row.setSourceItemName(sourceItemName);
            row.setTargetItemId(binding.getDevActId());
            row.setTargetItemName(resolveTargetItemName(binding, targetProductByKey));
            if (binding.getQuantity() != null) {
                row.setQuantityNumerator(binding.getQuantity());
                row.setQuantityDenominator(BigDecimal.ONE);
            }
            row.setUnitCode(binding.getUnitCode());
            row.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, binding.getUnitCode(),
                resolveTargetProductForBind(binding, targetProductByKey)));
            row.setPriority(priority);
            priority = priority + 1;
            row.setActiveFlag(PublicationStatus.ACTIVE.getCode().equals(binding.getStatusEnum()));
            rows.add(row);
        }
        return rows;
    }

    private Optional<BindQueryContext> resolveBindContext(String bindType) {
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.USAGE.getCode(),
                TableNameConstants.TER_CONCEPT,
                true
            ));
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.ACTIVITY.getCode(),
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                false
            ));
        }
        return Optional.empty();
    }

    private String resolveSourceItemName(
        BindQueryContext context,
        String sourceItemKey,
        Map<String, String> methodLabelByCode
    ) {
        if (StringUtils.isBlank(sourceItemKey)) {
            return null;
        }
        String key = sourceItemKey.trim();
        if (context.sourceIsMethodCode()) {
            String label = methodLabelByCode.get(key);
            if (StringUtils.isNotBlank(label)) {
                return label;
            }
            return key;
        }
        if (StringUtils.isNumeric(key)) {
            Long activityId = Long.valueOf(key);
            List<com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition> activityRows =
                activityDefinitionQueryService.findAllByIds(List.of(activityId));
            if (!activityRows.isEmpty() && activityRows.get(0) != null) {
                return activityRows.get(0).getName();
            }
        }
        return null;
    }

    private Long resolveSourceItemIdForDisplay(BindQueryContext context, String sourceItemKey) {
        if (context.sourceIsMethodCode()) {
            return null;
        }
        if (StringUtils.isBlank(sourceItemKey)) {
            return null;
        }
        String key = sourceItemKey.trim();
        if (StringUtils.isNumeric(key)) {
            return Long.valueOf(key);
        }
        return null;
    }

    private Map<String, ProductDefinitionDisplayDto> batchLoadTargetProducts(List<ServiceDeviceTemplateQueryModel> bindings) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (ServiceDeviceTemplateQueryModel binding : bindings) {
            if (binding.getDevActId() == null || StringUtils.isBlank(binding.getDevActTable())) {
                continue;
            }
            String table = binding.getDevActTable().trim();
            if (!isSupportedTargetTable(table)) {
                continue;
            }
            String dedupeKey = buildTargetDedupeKey(table, binding.getDevActId());
            if (!dedupe.add(dedupeKey)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(table);
            lookup.setProductDefinitionId(binding.getDevActId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private static boolean isSupportedTargetTable(String table) {
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            return true;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return true;
        }
        return false;
    }

    private static String buildTargetDedupeKey(String table, Long devActId) {
        return table + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR + devActId;
    }

    private String resolveTargetItemName(
        ServiceDeviceTemplateQueryModel binding,
        Map<String, ProductDefinitionDisplayDto> targetProductByKey
    ) {
        if (binding.getDevActId() == null || StringUtils.isBlank(binding.getDevActTable())) {
            return null;
        }
        String table = binding.getDevActTable().trim();
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, binding.getDevActId());
        ProductDefinitionDisplayDto product = targetProductByKey.get(key);
        if (product != null) {
            return product.getProductName();
        }
        return null;
    }

    private ProductDefinitionDisplayDto resolveTargetProductForBind(
        ServiceDeviceTemplateQueryModel binding,
        Map<String, ProductDefinitionDisplayDto> targetProductByKey
    ) {
        if (binding.getDevActId() == null || StringUtils.isBlank(binding.getDevActTable())) {
            return null;
        }
        String table = binding.getDevActTable().trim();
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, binding.getDevActId());
        return targetProductByKey.get(key);
    }

    private record BindQueryContext(String typeCode, String itemTable, boolean sourceIsMethodCode) {
    }
}
