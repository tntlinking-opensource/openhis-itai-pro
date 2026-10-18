/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.query;

import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceOrderBindRowDto;
import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import com.zxplat.application.shared.advice.SpecimenTubeMergeBindResolveSupport;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeGroupQueryModel;
import com.zxplat.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.zxplat.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.ServiceDeviceTemplateRangeCode;
import com.zxplat.domain.share.enums.ServiceDeviceTemplateTypeCode;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * IF-IP-DOR-12 / 护士执行：住院医嘱绑定行只读装载（Asm，无写库）。
 *
 * <p>经 {@link ServiceDeviceTemplateQueryService} 按住院范围与绑定类型查询启用态模板行，
 * 批量解析源项与目标项展示名（A.9.1）。{@code organizationUnitId} 预留机构维度过滤，当前模板域使用
 * {@link ServiceDeviceTemplateRangeCode#INPATIENT}，未下推机构 SQL。</p>
 *
 * <p><b>开立 vs 执行</b>：{@link #listDraftBindItems}（IF-IP-DOR-23）恒空——住院医生开立不带任何绑定
 * （诊疗/耗材/合管）。护士执行经 {@link #listBindingsBySourceItemKey} 挂未合管模板；已合管 activity 亦空。</p>
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
        // 缺绑定类型或主键时不查模板，避免误匹配全表
        if (bindType == null || itemId == null) {
            return new ArrayList<>();
        }
        return this.listBindingsBySourceItemKey(bindType, String.valueOf(itemId), organizationUnitId);
    }

    /**
     * 按绑定类型与来源项键装载绑定行（供护士执行 bindItems；开立 IF-IP-DOR-23 不走本方法）。
     *
     * <p>用法绑定：{@code sourceItemKey} 为药嘱 {@code method_code}（术语 code），与模板表
     * {@code cus_service_device_template.item_id} 精确匹配；诊疗绑定：{@code sourceItemKey} 为
     * {@code wkf_activity_definition.id} 字符串。检验已合管 → 空列表（合管开立/执行均不带）。</p>
     *
     * @param bindType           绑定来源类型（{@link DoctorStationAdviceConstants#BIND_TYPE_USAGE} /
     *                           {@link DoctorStationAdviceConstants#BIND_TYPE_ACTIVITY}）
     * @param sourceItemKey      模板来源项键（用法 code 或诊疗主键字符串）
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定行集合；合管/入参缺失/未知类型时返回可变空列表
     */
    public List<InpatientDoctorStationAdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId
    ) {
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows = new ArrayList<>();
        // 来源键缺失时不查模板
        if (bindType == null || StringUtils.isEmpty(sourceItemKey)) {
            return rows;
        }
        // 检验已合管：住院开立与执行均不带合管派生（采血费/试管等）
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(bindType)) {
            Long activityId = this.parseNumericSourceItemId(sourceItemKey);
            // 数字主键才可反查合管设备
            if (activityId != null) {
                Optional<SpecimenTubeMergeGroupQueryModel> mergeGroupRefOpt =
                    specimenTubeMergeBindResolveSupport.findMergeGroupRefByActivityId(activityId);
                // 已合管 → 空列表（不回落 cus_service_device_template）
                if (mergeGroupRefOpt.isPresent()) {
                    return rows;
                }
            }
        }
        return this.listTemplateBindingsBySourceItemKey(bindType, sourceItemKey, organizationUnitId);
    }

    /**
     * 仅读住院范围 {@code cus_service_device_template}（不含合管；合管在
     * {@link #listBindingsBySourceItemKey} 已短路为空）。
     *
     * @param bindType           绑定来源类型
     * @param sourceItemKey      模板来源项键
     * @param organizationUnitId 机构单元
     * @return 模板绑定行；无命中为空列表
     */
    public List<InpatientDoctorStationAdviceOrderBindRowDto> listTemplateBindingsBySourceItemKey(
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
     * IF-IP-DOR-23 同构读口：住院开立<strong>不</strong>解析任何绑定（诊疗/耗材/合管均不带）。
     *
     * <p>与门诊 IF-DOR-23 差异：本方法恒返回空列表。住院范围 {@code cus_service_device_template}
     * 仅由护士执行列表挂载（**BR-IP-EXE-013**，未合管）；已合管 activity 开立与执行均不带
     * （**AS-BAS-CFG-001 §7.7** / **BR-IP-ORD-036**）。入参保留与门诊同构签名供前端复用适配器。</p>
     *
     * @param lines              暂存变更行（本方法不消费）
     * @param organizationUnitId 机构单元（本方法不消费）
     * @return 恒为空列表
     */
    public List<DoctorStationAdviceDraftBindItemDto> listDraftBindItems(
        List<InpatientDoctorStationAdviceDraftLineRequest> lines,
        Long organizationUnitId
    ) {
        // BR-IP-ORD-036：住院开立不带绑定；未合管挂执行侧，合管执行亦不带
        return new ArrayList<>();
    }

    /**
     * 绑定类型 → 模板 typeCode/itemTable 与来源是否用法 code。
     *
     * @param bindType 绑定类型业务码
     * @return 查询上下文；未知类型 empty
     */
    private Optional<BindQueryContext> resolveBindContext(String bindType) {
        // 用法绑定：来源 ter_concept，item_id=method_code
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.USAGE.getCode(),
                TableNameConstants.TER_CONCEPT,
                true
            ));
        }
        // 诊疗绑定：来源 wkf_activity_definition
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(bindType)) {
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.ACTIVITY.getCode(),
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                false
            ));
        }
        // 未知 bindType：调用方得 empty，不查库
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
            List<com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition> rows =
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
