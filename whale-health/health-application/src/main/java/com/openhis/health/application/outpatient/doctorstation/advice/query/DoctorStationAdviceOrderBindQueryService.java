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

import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceOrderBindRowDto;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
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
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
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
 * <p>与住院 {@code InpatientDoctorStationAdviceOrderBindQueryService} 同构：经
 * {@link ServiceDeviceTemplateQueryService} 查询全院启用态模板，批量解析展示名（A.9.1）。
 * 包级隔离，禁止跨站 import 住院查询服务。</p>
 */
@Service
@RequiredArgsConstructor
public class DoctorStationAdviceOrderBindQueryService {

    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 按绑定类型与主数据标识装载绑定行。
     *
     * <p><b>规模（B.2）</b>：返回行数上限 {@link DoctorStationAdviceConstants#ORDER_BIND_LIST_MAX_ROWS}。</p>
     *
     * @param bindType            绑定类型业务码（{@link DoctorStationAdviceConstants#BIND_TYPE_USAGE_TO_DEVICE} /
     *                            {@link DoctorStationAdviceConstants#BIND_TYPE_ACTIVITY_CHILD}）
     * @param itemId              主数据主键（已由 Mapper 从业务号/数字 id 解析）
     * @param organizationUnitId  机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合；入参缺失或未知绑定类型时返回可变空列表
     */
    public List<DoctorStationAdviceOrderBindRowDto> listBindings(String bindType, Long itemId, Long organizationUnitId) {
        List<DoctorStationAdviceOrderBindRowDto> rows = new ArrayList<>();
        // 门控：缺少绑定类型或主键时无法定位模板来源项。
        if (bindType == null || itemId == null) {
            return rows;
        }
        Optional<BindQueryContext> contextOpt = resolveBindContext(bindType);
        if (contextOpt.isEmpty()) {
            return rows;
        }
        BindQueryContext context = contextOpt.get();
        String rangeCode = ServiceDeviceTemplateRangeCode.HOSPITAL.getCode();
        String itemIdKey = String.valueOf(itemId);
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
        String sourceItemName = resolveSourceItemName(context, itemId);
        Map<String, ProductDefinitionDisplayDto> targetProductByKey = batchLoadTargetProducts(bindings);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        int priority = 1;
        for (int i = 0; i < bound; i++) {
            ServiceDeviceTemplateQueryModel binding = bindings.get(i);
            DoctorStationAdviceOrderBindRowDto row = new DoctorStationAdviceOrderBindRowDto();
            row.setBindType(bindType);
            row.setSourceItemId(itemId);
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

    /**
     * 将前端 bindType 契约码映射为领域模板查询上下文。
     *
     * @param bindType 前端传入的绑定类型码
     * @return 命中则返回查询上下文，否则空
     */
    private Optional<BindQueryContext> resolveBindContext(String bindType) {
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE_TO_DEVICE.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.USAGE.getCode(),
                TableNameConstants.TER_CONCEPT,
                true
            ));
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY_CHILD.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.ACTIVITY.getCode(),
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                false
            ));
        }
        return Optional.empty();
    }

    /**
     * 解析绑定来源项展示名（用法概念或诊疗活动定义）。
     *
     * @param context 绑定查询上下文
     * @param itemId  来源主键
     * @return 展示名；未命中时 null
     */
    private String resolveSourceItemName(BindQueryContext context, Long itemId) {
        if (context.sourceIsConcept()) {
            Optional<Concept> concept = codeSystemQueryService.findConceptById(itemId);
            if (concept.isPresent()) {
                return concept.get().getDisplay();
            }
            return null;
        }
        List<com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition> rows =
            activityDefinitionQueryService.findAllByIds(List.of(itemId));
        if (!rows.isEmpty() && rows.get(0) != null) {
            return rows.get(0).getName();
        }
        return null;
    }

    /**
     * 批量加载绑定目标项（诊疗/耗材定义）的产品展示 DTO。
     *
     * @param bindings 模板绑定行
     * @return 复合键到展示 DTO 的映射
     */
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

    /**
     * 判断目标表是否支持经产品批量加载解析展示名。
     *
     * @param table 持久化表名
     * @return 支持则 true
     */
    private static boolean isSupportedTargetTable(String table) {
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            return true;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return true;
        }
        return false;
    }

    /**
     * 构造批量去重键（表名 + SOH + 定义主键）。
     *
     * @param table 目标表名
     * @param devActId 目标定义主键
     * @return 去重键
     */
    private static String buildTargetDedupeKey(String table, Long devActId) {
        return table + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR + devActId;
    }

    /**
     * 从批量展示 DTO 映射中解析单条绑定目标的展示名。
     *
     * @param binding 模板绑定行
     * @param targetProductByKey 批量展示 DTO
     * @return 展示名；未命中时 null
     */
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

    /**
     * 从批量展示 DTO 映射中解析绑定目标产品行（单位展示名回落）。
     */
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

    private record BindQueryContext(String typeCode, String itemTable, boolean sourceIsConcept) {
    }
}
