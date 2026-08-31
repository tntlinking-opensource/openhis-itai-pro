/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.query;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceOrderBindRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientMedicationAdviceDraftLinePayload;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientServiceAdviceDraftLinePayload;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import com.openhis.health.application.shared.advice.SpecimenTubeMergeBindResolveSupport;
import com.openhis.health.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeDerivedBindQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
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
import com.openhis.whale.core.application.util.StringUtils;
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
 * IF-IP-DOR-12：住院医嘱绑定行只读装载（Asm，无写库）。
 *
 * <p>经 {@link ServiceDeviceTemplateQueryService} 按住院范围与绑定类型查询启用态模板行，
 * 批量解析源项与目标项展示名（A.9.1）。{@code organizationUnitId} 预留机构维度过滤，当前模板域使用
 * {@link ServiceDeviceTemplateRangeCode#INPATIENT}，未下推机构 SQL。</p>
 *
 * <p>绑定类型：用法→关联耗材、诊疗套餐子项（常量见 {@link DoctorStationAdviceConstants}）。</p>
 */
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceOrderBindQueryService {

    /** 服务-物资绑定模板读侧（全院范围、按来源项查询）。 */
    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 定价行：绑定目标单价/definitionId。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 临床目录统一单价解析。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 标本合管派生绑定解析（Phase 2）。 */
    private final SpecimenTubeMergeBindResolveSupport specimenTubeMergeBindResolveSupport;

    /**
     * 按绑定类型与主数据主键装载绑定行（诊疗来源等 {@code item_id} 存数字主键的场景）。
     *
     * @param bindType           绑定类型业务码
     * @param itemId             主数据主键
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合
     */
    public List<InpatientDoctorStationAdviceOrderBindRowDto> listBindings(
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
     * <p>用法绑定：{@code sourceItemKey} 为药嘱 {@code method_code}（术语 code），与模板表
     * {@code cus_service_device_template.item_id} 精确匹配；诊疗绑定：{@code sourceItemKey} 为
     * {@code wkf_activity_definition.id} 字符串。</p>
     *
     * @param bindType           绑定来源类型（{@link DoctorStationAdviceConstants#BIND_TYPE_USAGE} /
     *                           {@link DoctorStationAdviceConstants#BIND_TYPE_ACTIVITY}）
     * @param sourceItemKey      模板来源项键（用法 code 或诊疗主键字符串）
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合；入参缺失或未知绑定类型时返回可变空列表
     */
    public List<InpatientDoctorStationAdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId
    ) {
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows = new ArrayList<>();
        if (bindType == null || StringUtils.isEmpty(sourceItemKey)) {
            return rows;
        }
        Optional<BindQueryContext> contextOpt = resolveBindContext(bindType);
        if (contextOpt.isEmpty()) {
            return rows;
        }
        BindQueryContext context = contextOpt.get();
        // 检验 activity 已配置合管时优先读合管派生绑定，不查 service_device_template
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(bindType)) {
            Long activityId = this.parseNumericSourceItemId(sourceItemKey);
            if (activityId != null) {
                Optional<Long> mergeDeviceIdOpt =
                    specimenTubeMergeBindResolveSupport.findMergeDeviceIdByActivityId(activityId);
                if (mergeDeviceIdOpt.isPresent()) {
                    return this.assembleInpatientMergeBindRows(
                        bindType, sourceItemKey, mergeDeviceIdOpt.get(), organizationUnitId);
                }
            }
        }
        // 住院医嘱/护士执行仅匹配住院范围绑定模板
        String rangeCode = ServiceDeviceTemplateRangeCode.INPATIENT.getCode();
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
        Map<String, List<ChargeItemDefinition>> priceByProductKey = this.loadRetailPricesByProductKey(bindings);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        int priority = 1;
        for (int i = 0; i < bound; i++) {
            ServiceDeviceTemplateQueryModel binding = bindings.get(i);
            InpatientDoctorStationAdviceOrderBindRowDto row = new InpatientDoctorStationAdviceOrderBindRowDto();
            row.setBindingId(binding.getId());
            row.setBindType(bindType);
            row.setSourceItemId(this.resolveSourceItemIdForDisplay(context, itemIdKey));
            row.setSourceItemName(sourceItemName);
            row.setTargetItemId(binding.getDevActId());
            if (StringUtils.isNotEmpty(binding.getDevActTable())) {
                row.setTargetItemTable(binding.getDevActTable().trim());
            }
            row.setTargetItemName(resolveTargetItemName(binding, targetProductByKey));
            ProductDefinitionDisplayDto targetProduct =
                resolveTargetProductForBind(binding, targetProductByKey);
            if (targetProduct != null) {
                row.setBusNo(targetProduct.getProductBusNo());
                row.setSpecificationText(this.resolveSpecificationText(targetProduct));
            }
            if (StringUtils.isNotEmpty(binding.getDevActTable()) && binding.getDevActId() != null) {
                String priceKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    binding.getDevActTable().trim(), binding.getDevActId());
                List<ChargeItemDefinition> priceLines = priceByProductKey.getOrDefault(priceKey, List.of());
                String categoryCode = null;
                if (targetProduct != null) {
                    categoryCode = targetProduct.getCategoryCode();
                }
                ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                    .productTableName(binding.getDevActTable().trim())
                    .productDefinitionId(binding.getDevActId())
                    .categoryCode(categoryCode)
                    .organizationId(organizationUnitId)
                    .priceLines(priceLines)
                    .productRowForOccupancy(targetProduct)
                    .build();
                ChargeItemDefinition selected =
                    clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
                if (selected != null) {
                    row.setChargeItemDefinitionId(selected.getId());
                    row.setUnitPrice(selected.getPriceValue());
                } else if (targetProduct != null && targetProduct.getChargeItemDefinitionId() != null) {
                    row.setChargeItemDefinitionId(targetProduct.getChargeItemDefinitionId());
                }
            }
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
     * IF-IP-DOR-23：按暂存变更行解析直接绑定（合管优先；同批同设备去重）。
     *
     * @param lines              暂存变更行
     * @param organizationUnitId 机构单元
     * @return 绑定明细
     */
    public List<DoctorStationAdviceDraftBindItemDto> listDraftBindItems(
        List<InpatientDoctorStationAdviceDraftLineRequest> lines,
        Long organizationUnitId
    ) {
        List<DoctorStationAdviceDraftBindItemDto> bindItems = new ArrayList<>();
        Set<Long> emittedMergeDeviceIds = new LinkedHashSet<>();
        if (lines == null || lines.isEmpty()) {
            return bindItems;
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            InpatientDoctorStationAdviceDraftLineRequest line = lines.get(lineIndex);
            if (line == null) {
                continue;
            }
            String lineOp = StringUtils.trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
            if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(lineOp)) {
                continue;
            }
            String productTableName = StringUtils.trimToEmpty(line.getProductTableName());
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
                this.appendInpatientMedicationDraftBindItems(
                    bindItems, line, lineIndex, lineOp, organizationUnitId);
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTableName)) {
                this.appendInpatientServiceDraftBindItems(
                    bindItems, line, lineIndex, lineOp, organizationUnitId, emittedMergeDeviceIds);
            }
        }
        return bindItems;
    }

    /**
     * 住院药品行 USAGE 绑定解析。
     */
    private void appendInpatientMedicationDraftBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        InpatientDoctorStationAdviceDraftLineRequest line,
        int lineIndex,
        String lineOp,
        Long organizationUnitId
    ) {
        SaveInpatientMedicationAdviceDraftLinePayload payload = line.getMedicationPayload();
        if (payload == null || StringUtils.isEmpty(payload.getMethodCode())) {
            return;
        }
        Long definitionId = payload.getMedicationDefinitionId();
        if (definitionId == null) {
            return;
        }
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows = this.listBindingsBySourceItemKey(
            DoctorStationAdviceConstants.BIND_TYPE_USAGE,
            payload.getMethodCode().trim(),
            organizationUnitId
        );
        this.appendInpatientDraftBindItems(
            bindItems, rows, lineIndex, lineOp,
            TableNameConstants.MED_MEDICATION_DEFINITION, definitionId,
            line.getRequestId(), TableNameConstants.MED_MEDICATION_REQUEST, null);
    }

    /**
     * 住院诊疗行绑定解析（合管优先）。
     */
    private void appendInpatientServiceDraftBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        InpatientDoctorStationAdviceDraftLineRequest line,
        int lineIndex,
        String lineOp,
        Long organizationUnitId,
        Set<Long> emittedMergeDeviceIds
    ) {
        SaveInpatientServiceAdviceDraftLinePayload payload = line.getServicePayload();
        if (payload == null || payload.getActivityDefinitionId() == null) {
            return;
        }
        Long activityId = payload.getActivityDefinitionId();
        Optional<Long> mergeDeviceIdOpt = specimenTubeMergeBindResolveSupport.findMergeDeviceIdByActivityId(activityId);
        if (mergeDeviceIdOpt.isPresent()) {
            Long mergeDeviceId = mergeDeviceIdOpt.get();
            if (emittedMergeDeviceIds.contains(mergeDeviceId)) {
                return;
            }
            emittedMergeDeviceIds.add(mergeDeviceId);
            List<InpatientDoctorStationAdviceOrderBindRowDto> rows = this.assembleInpatientMergeBindRows(
                DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY,
                String.valueOf(activityId),
                mergeDeviceId,
                organizationUnitId
            );
            this.appendInpatientDraftBindItems(
                bindItems, rows, lineIndex, lineOp,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId,
                line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST, mergeDeviceId);
            return;
        }
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows = this.listBindingsBySourceItemKey(
            DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY,
            String.valueOf(activityId),
            organizationUnitId
        );
        this.appendInpatientDraftBindItems(
            bindItems, rows, lineIndex, lineOp,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId,
            line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST, null);
    }

    /**
     * 住院绑定行 → IF-IP-DOR-23 明细。
     */
    private void appendInpatientDraftBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows,
        int lineIndex,
        String lineOp,
        String triggerProductTableName,
        Long triggerDefinitionId,
        Long triggerRequestId,
        String basedOnTable,
        Long specimenTubeMergeDeviceId
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (InpatientDoctorStationAdviceOrderBindRowDto row : rows) {
            DoctorStationAdviceDraftBindItemDto item = new DoctorStationAdviceDraftBindItemDto();
            item.setBindType(row.getBindType());
            item.setSourceItemId(row.getSourceItemId());
            item.setSourceItemName(row.getSourceItemName());
            item.setTargetItemId(row.getTargetItemId());
            item.setTargetItemName(row.getTargetItemName());
            if (StringUtils.isNotEmpty(row.getTargetItemTable())) {
                item.setTargetProductTableName(row.getTargetItemTable().trim());
            }
            item.setQuantityNumerator(row.getQuantityNumerator());
            item.setQuantityDenominator(row.getQuantityDenominator());
            item.setUnitCode(row.getUnitCode());
            item.setUnitName(row.getUnitName());
            item.setPriority(row.getPriority());
            item.setActiveFlag(row.getActiveFlag());
            item.setTriggerLineIndex(lineIndex);
            item.setTriggerLineOp(lineOp);
            item.setTriggerProductTableName(triggerProductTableName);
            item.setTriggerDefinitionId(triggerDefinitionId);
            item.setTriggerRequestId(triggerRequestId);
            item.setBasedOnTable(basedOnTable);
            item.setBasedOnRequestId(triggerRequestId);
            item.setSpecimenTubeMergeDeviceId(specimenTubeMergeDeviceId);
            bindItems.add(item);
        }
    }

    /**
     * 绑定类型 → 模板 typeCode/itemTable 与来源是否用法 code。
     *
     * @param bindType 绑定类型业务码
     * @return 查询上下文；未知类型 empty
     */
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

    /**
     * 来源项展示名：用法 code 查术语 label，诊疗 id 查活动定义名称。
     *
     * @param context           绑定上下文
     * @param sourceItemKey     来源键
     * @param methodLabelByCode   用法 code → 展示名
     * @return 展示名；无法解析则 null
     */
    private String resolveSourceItemName(
        BindQueryContext context,
        String sourceItemKey,
        Map<String, String> methodLabelByCode
    ) {
        if (StringUtils.isEmpty(sourceItemKey)) {
            return null;
        }
        String key = sourceItemKey.trim();
        if (context.sourceIsMethodCode()) {
            String label = methodLabelByCode.get(key);
            if (StringUtils.isNotEmpty(label)) {
                return label;
            }
            return key;
        }
        if (StringUtils.isNotEmpty(key) && key.chars().allMatch(Character::isDigit)) {
            Long activityId = Long.valueOf(key);
            List<com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition> rows =
                activityDefinitionQueryService.findAllByIds(List.of(activityId));
            if (!rows.isEmpty() && rows.get(0) != null) {
                return rows.get(0).getName();
            }
        }
        return null;
    }

    /**
     * 来源项 id 展示：用法绑定返回 null，诊疗绑定解析数字主键。
     *
     * @param context       绑定上下文
     * @param sourceItemKey 来源键
     * @return 展示用 Long id；用法或非法键则 null
     */
    private Long resolveSourceItemIdForDisplay(BindQueryContext context, String sourceItemKey) {
        if (context.sourceIsMethodCode()) {
            return null;
        }
        if (StringUtils.isEmpty(sourceItemKey)) {
            return null;
        }
        String key = sourceItemKey.trim();
        if (StringUtils.isNotEmpty(key) && key.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(key);
        }
        return null;
    }

    /**
     * 批量加载绑定目标产品/诊疗展示 DTO（去重 table+id）。
     *
     * @param bindings 模板绑定行
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadTargetProducts(List<ServiceDeviceTemplateQueryModel> bindings) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (ServiceDeviceTemplateQueryModel binding : bindings) {
            if (binding.getDevActId() == null || StringUtils.isEmpty(binding.getDevActTable())) {
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
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 绑定目标表是否为产品目录白名单（诊疗/耗材定义）。
     *
     * @param table 目标 itemTable
     * @return 支持为 true
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
     * 目标产品批量加载去重键。
     *
     * @param table    品项表
     * @param devActId 目标 id
     * @return table + 分隔符 + id
     */
    private static String buildTargetDedupeKey(String table, Long devActId) {
        return table + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR + devActId;
    }

    /**
     * 绑定目标展示名（产品目录 productName）。
     *
     * @param binding             模板行
     * @param targetProductByKey  预加载产品 Map
     * @return 目标名称；无产品则 null
     */
    private String resolveTargetItemName(
        ServiceDeviceTemplateQueryModel binding,
        Map<String, ProductDefinitionDisplayDto> targetProductByKey
    ) {
        if (binding.getDevActId() == null || StringUtils.isEmpty(binding.getDevActTable())) {
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
     * 绑定行对应的目标产品展示 DTO。
     *
     * @param binding             模板行
     * @param targetProductByKey  预加载产品 Map
     * @return 产品 DTO；键缺失则 null
     */
    private ProductDefinitionDisplayDto resolveTargetProductForBind(
        ServiceDeviceTemplateQueryModel binding,
        Map<String, ProductDefinitionDisplayDto> targetProductByKey
    ) {
        if (binding.getDevActId() == null || StringUtils.isEmpty(binding.getDevActTable())) {
            return null;
        }
        String table = binding.getDevActTable().trim();
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, binding.getDevActId());
        return targetProductByKey.get(key);
    }

    /**
     * 绑定查询上下文：模板 typeCode、来源 itemTable、是否用法 code 来源。
     *
     * @param typeCode           模板类型 code
     * @param itemTable          来源 itemTable
     * @param sourceIsMethodCode 来源是否为用法 code
     */
    private record BindQueryContext(String typeCode, String itemTable, boolean sourceIsMethodCode) {
    }

    /**
     * 按绑定目标批量加载零售价目行并按产品复合键分组。
     *
     * @param bindings 模板绑定行
     * @return 产品复合键 → 定价行列表
     */
    private Map<String, List<ChargeItemDefinition>> loadRetailPricesByProductKey(
        List<ServiceDeviceTemplateQueryModel> bindings) {
        Map<String, List<Long>> idsByTable = new HashMap<>();
        for (ServiceDeviceTemplateQueryModel binding : bindings) {
            if (binding == null || binding.getDevActId() == null || StringUtils.isEmpty(binding.getDevActTable())) {
                continue;
            }
            String table = binding.getDevActTable().trim();
            if (!isSupportedTargetTable(table)) {
                continue;
            }
            idsByTable.computeIfAbsent(table, key -> new ArrayList<>()).add(binding.getDevActId());
        }
        Map<String, List<ChargeItemDefinition>> grouped = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : idsByTable.entrySet()) {
            ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
            cmd.setInstanceTable(entry.getKey());
            cmd.setInstanceIds(entry.getValue());
            List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
            for (ChargeItemDefinition line : lines) {
                if (line == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getInstanceTable(), line.getInstanceId());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
            }
        }
        return grouped;
    }

    /**
     * 产品规格展示：优先 totalVolume。
     *
     * @param product 产品展示 DTO
     * @return 规格文案；无则 null
     */
    private String resolveSpecificationText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(product.getTotalVolume())) {
            return product.getTotalVolume().trim();
        }
        return null;
    }

    /**
     * 合管配置派生绑定 → 住院绑定行 DTO（含零售单价 enrich）。
     *
     * @param bindType           绑定类型
     * @param sourceItemKey      触发 activity 主键字符串
     * @param mergeDeviceId      合管设备主键
     * @param organizationUnitId 机构单元
     * @return 绑定行列表
     */
    private List<InpatientDoctorStationAdviceOrderBindRowDto> assembleInpatientMergeBindRows(
        String bindType,
        String sourceItemKey,
        Long mergeDeviceId,
        Long organizationUnitId
    ) {
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows = new ArrayList<>();
        if (mergeDeviceId == null || StringUtils.isEmpty(sourceItemKey)) {
            return rows;
        }
        Optional<BindQueryContext> contextOpt = resolveBindContext(bindType);
        if (contextOpt.isEmpty()) {
            return rows;
        }
        BindQueryContext context = contextOpt.get();
        String sourceItemName = resolveSourceItemName(context, sourceItemKey.trim(), new HashMap<>());
        List<SpecimenTubeMergeDerivedBindQueryModel> derived =
            specimenTubeMergeBindResolveSupport.listDerivedBindsByDeviceId(mergeDeviceId);
        if (derived.isEmpty()) {
            return rows;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SpecimenTubeMergeDerivedBindQueryModel bind : derived) {
            if (bind == null || bind.getTargetId() == null || StringUtils.isEmpty(bind.getTargetTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(bind.getTargetTable().trim());
            lookup.setProductDefinitionId(bind.getTargetId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> targetProductByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<String, List<ChargeItemDefinition>> priceByProductKey =
            this.loadRetailPricesByProductKeys(lookups);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        int priority = 1;
        for (SpecimenTubeMergeDerivedBindQueryModel bind : derived) {
            if (bind == null || bind.getTargetId() == null || StringUtils.isEmpty(bind.getTargetTable())) {
                continue;
            }
            String targetTable = bind.getTargetTable().trim();
            InpatientDoctorStationAdviceOrderBindRowDto row = new InpatientDoctorStationAdviceOrderBindRowDto();
            row.setBindingId(bind.getMergeConfigLineId());
            row.setBindType(bindType);
            row.setSourceItemId(this.parseNumericSourceItemId(sourceItemKey));
            row.setSourceItemName(sourceItemName);
            row.setTargetItemId(bind.getTargetId());
            row.setTargetItemTable(targetTable);
            row.setTargetItemName(bind.getTargetName());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(targetTable, bind.getTargetId());
            ProductDefinitionDisplayDto targetProduct = targetProductByKey.get(productKey);
            if (targetProduct != null) {
                row.setBusNo(targetProduct.getProductBusNo());
                row.setSpecificationText(this.resolveSpecificationText(targetProduct));
            }
            List<ChargeItemDefinition> priceLines = priceByProductKey.getOrDefault(productKey, List.of());
            String categoryCode = null;
            if (targetProduct != null) {
                categoryCode = targetProduct.getCategoryCode();
            }
            ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(targetTable)
                .productDefinitionId(bind.getTargetId())
                .categoryCode(categoryCode)
                .organizationId(organizationUnitId)
                .priceLines(priceLines)
                .productRowForOccupancy(targetProduct)
                .build();
            ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
            if (selected != null) {
                row.setChargeItemDefinitionId(selected.getId());
                row.setUnitPrice(selected.getPriceValue());
            } else if (targetProduct != null && targetProduct.getChargeItemDefinitionId() != null) {
                row.setChargeItemDefinitionId(targetProduct.getChargeItemDefinitionId());
            }
            if (bind.getQuantity() != null) {
                row.setQuantityNumerator(bind.getQuantity());
                row.setQuantityDenominator(BigDecimal.ONE);
            } else {
                row.setQuantityNumerator(BigDecimal.ONE);
                row.setQuantityDenominator(BigDecimal.ONE);
            }
            row.setUnitCode(bind.getUnitCode());
            row.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, bind.getUnitCode(), targetProduct));
            row.setPriority(priority);
            priority = priority + 1;
            row.setActiveFlag(Boolean.TRUE);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量加载目标品项零售价行。
     *
     * @param lookups 品项批量键
     * @return 复合键 → 定价行列表
     */
    private Map<String, List<ChargeItemDefinition>> loadRetailPricesByProductKeys(
        List<ProductDefinitionBatchLookup> lookups
    ) {
        Map<String, List<Long>> idsByTable = new HashMap<>();
        for (ProductDefinitionBatchLookup lookup : lookups) {
            if (lookup == null || lookup.getProductDefinitionId() == null
                || StringUtils.isEmpty(lookup.getProductTableName())) {
                continue;
            }
            String table = lookup.getProductTableName().trim();
            idsByTable.computeIfAbsent(table, key -> new ArrayList<>()).add(lookup.getProductDefinitionId());
        }
        Map<String, List<ChargeItemDefinition>> grouped = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : idsByTable.entrySet()) {
            ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
            cmd.setInstanceTable(entry.getKey());
            cmd.setInstanceIds(entry.getValue());
            List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
            for (ChargeItemDefinition line : lines) {
                if (line == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getInstanceTable(), line.getInstanceId());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
            }
        }
        return grouped;
    }

    /**
     * 解析数字型来源项键。
     *
     * @param sourceItemKey 来源键
     * @return 数字主键；非法时 null
     */
    private Long parseNumericSourceItemId(String sourceItemKey) {
        if (StringUtils.isEmpty(sourceItemKey)) {
            return null;
        }
        String key = sourceItemKey.trim();
        if (StringUtils.isNotEmpty(key) && key.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(key);
        }
        return null;
    }
}
