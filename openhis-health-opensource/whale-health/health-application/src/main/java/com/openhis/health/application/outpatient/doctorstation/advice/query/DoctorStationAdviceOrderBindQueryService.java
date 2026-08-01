/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.query;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceOrderBindRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveMedicationAdviceDraftLinePayload;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveServiceAdviceDraftLinePayload;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
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
 * IF-DOR-12 / IF-DOR-23：门诊医嘱绑定行只读装载。
 *
 * <p>与住院 {@code InpatientDoctorStationAdviceOrderBindQueryService} 同构：用法来源键为
 * {@code method_code} 术语 code；诊疗来源键为活动定义主键字符串。经
 * {@link ServiceDeviceTemplateQueryService} 查询门诊范围启用态模板，批量解析展示名（A.9.1）。</p>
 *
 * <p>应用层读服务：跨聚合读经域 {@code *QueryService} 与 {@code ProductDefinitionDisplayTerminologyAssembler}，
 * 不直接注入 Repository（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class DoctorStationAdviceOrderBindQueryService {

    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
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
            // 绑定类型或主键缺失时不查模板
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
        if (bindType == null || StringUtils.isEmpty(sourceItemKey)) {
            // 来源键缺失时不查模板
            return rows;
        }
        List<BindAssemblyRow> assemblyRows = this.assembleBindRows(bindType, sourceItemKey, organizationUnitId);
        for (BindAssemblyRow assemblyRow : assemblyRows) {
            rows.add(assemblyRow.row());
        }
        return rows;
    }

    /**
     * IF-DOR-23：按暂存变更行解析直接绑定（不含级联、不去重）。
     *
     * @param lines              IF-DOR-14 同构暂存变更行
     * @param organizationUnitId 机构单元过滤（预留，当前未参与模板查询）
     * @return 绑定明细；跳过 DELETE 行及无法解析来源键的行
     */
    public List<DoctorStationAdviceDraftBindItemDto> listDraftBindItems(
        List<DoctorStationAdviceDraftLineRequest> lines,
        Long organizationUnitId
    ) {
        List<DoctorStationAdviceDraftBindItemDto> bindItems = new ArrayList<>();
        // 空变更行集合不产生绑定解析，与 IF-DOR-14 空列表语义一致
        if (lines == null || lines.isEmpty()) {
            return bindItems;
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            DoctorStationAdviceDraftLineRequest line = lines.get(lineIndex);
            // 忽略前端误传的空行，避免 NPE 中断整批解析
            if (line == null) {
                continue;
            }
            String lineOp = StringUtils.trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
            // DELETE 行不产生新的绑定解析结果
            if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(lineOp)) {
                continue;
            }
            String productTableName = StringUtils.trimToEmpty(line.getProductTableName());
            // 药品行：按 method_code 查 USAGE 直接绑定（BR-ORD-029 禁止级联）
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
                this.appendMedicationLineBindItems(bindItems, line, lineIndex, lineOp, organizationUnitId);
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTableName)) {
                // 诊疗行：按 activityDefinitionId 查 ACTIVITY 直接绑定
                this.appendServiceLineBindItems(bindItems, line, lineIndex, lineOp, organizationUnitId);
            }
        }
        return bindItems;
    }

    /**
     * 解析单条药品暂存行的 USAGE 直接绑定并追加到结果集。
     *
     * @param bindItems            输出集合
     * @param line                 暂存变更行
     * @param lineIndex            变更行在 Request.lines 中的下标
     * @param lineOp               行操作码
     * @param organizationUnitId   机构单元过滤（预留）
     */
    private void appendMedicationLineBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        DoctorStationAdviceDraftLineRequest line,
        int lineIndex,
        String lineOp,
        Long organizationUnitId
    ) {
        SaveMedicationAdviceDraftLinePayload payload = line.getMedicationPayload();
        if (payload == null || StringUtils.isEmpty(payload.getMethodCode())) {
            // 未选用法时不触发 USAGE 绑定
            return;
        }
        Long definitionId = payload.getMedicationDefinitionId();
        if (definitionId == null) {
            // 缺药品定义主键无法标注触发源，跳过该行
            return;
        }
        List<BindAssemblyRow> assemblyRows = this.assembleBindRows(
            DoctorStationAdviceConstants.BIND_TYPE_USAGE,
            payload.getMethodCode().trim(),
            organizationUnitId
        );
        this.appendDraftBindItems(
            bindItems,
            assemblyRows,
            lineIndex,
            lineOp,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            definitionId,
            line.getRequestId(),
            TableNameConstants.MED_MEDICATION_REQUEST
        );
    }

    /**
     * 解析单条诊疗暂存行的 ACTIVITY 直接绑定并追加到结果集。
     *
     * @param bindItems            输出集合
     * @param line                 暂存变更行
     * @param lineIndex            变更行在 Request.lines 中的下标
     * @param lineOp               行操作码
     * @param organizationUnitId   机构单元过滤（预留）
     */
    private void appendServiceLineBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        DoctorStationAdviceDraftLineRequest line,
        int lineIndex,
        String lineOp,
        Long organizationUnitId
    ) {
        SaveServiceAdviceDraftLinePayload payload = line.getServicePayload();
        if (payload == null || payload.getActivityDefinitionId() == null) {
            // 缺诊疗载荷或活动定义主键时无法解析 ACTIVITY 绑定
            return;
        }
        List<BindAssemblyRow> assemblyRows = this.assembleBindRows(
            DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY,
            String.valueOf(payload.getActivityDefinitionId()),
            organizationUnitId
        );
        this.appendDraftBindItems(
            bindItems,
            assemblyRows,
            lineIndex,
            lineOp,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            payload.getActivityDefinitionId(),
            line.getRequestId(),
            TableNameConstants.WKF_SERVICE_REQUEST
        );
    }

    /**
     * 将模板绑定行转为 IF-DOR-23 明细，并回填触发源与 based_on 锚点。
     *
     * @param bindItems              输出集合
     * @param assemblyRows           模板绑定装配行
     * @param lineIndex              触发变更行下标
     * @param lineOp                 触发行操作码
     * @param triggerProductTableName 触发主医嘱产品表
     * @param triggerDefinitionId    触发主医嘱定义主键
     * @param triggerRequestId       触发主医嘱请求主键（CREATE 可为空）
     * @param basedOnTable           派生行 based_on 表名
     */
    private void appendDraftBindItems(
        List<DoctorStationAdviceDraftBindItemDto> bindItems,
        List<BindAssemblyRow> assemblyRows,
        int lineIndex,
        String lineOp,
        String triggerProductTableName,
        Long triggerDefinitionId,
        Long triggerRequestId,
        String basedOnTable
    ) {
        for (BindAssemblyRow assemblyRow : assemblyRows) {
            DoctorStationAdviceOrderBindRowDto row = assemblyRow.row();
            DoctorStationAdviceDraftBindItemDto item = new DoctorStationAdviceDraftBindItemDto();
            item.setBindType(row.getBindType());
            item.setSourceItemId(row.getSourceItemId());
            item.setSourceItemName(row.getSourceItemName());
            item.setTargetItemId(row.getTargetItemId());
            item.setTargetItemName(row.getTargetItemName());
            item.setTargetProductTableName(assemblyRow.targetProductTableName());
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
            bindItems.add(item);
        }
    }

    /**
     * 按绑定类型与来源键装载模板行并批量解析展示名（IF-DOR-12/23 共用内核）。
     *
     * @param bindType           绑定类型业务码
     * @param sourceItemKey      模板来源项键
     * @param organizationUnitId 机构单元过滤（预留，当前未参与查询）
     * @return 绑定装配行；未知类型或空来源时返回空列表
     */
    private List<BindAssemblyRow> assembleBindRows(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId
    ) {
        List<BindAssemblyRow> rows = new ArrayList<>();
        if (bindType == null || StringUtils.isEmpty(sourceItemKey)) {
            // 来源键缺失时不查模板，避免全表误匹配
            return rows;
        }
        Optional<BindQueryContext> contextOpt = resolveBindContext(bindType);
        if (contextOpt.isEmpty()) {
            // 非 USAGE/ACTIVITY 的 bindType 直接忽略
            return rows;
        }
        BindQueryContext context = contextOpt.get();
        // 门诊医嘱仅匹配门诊范围绑定模板
        String rangeCode = ServiceDeviceTemplateRangeCode.OUTPATIENT.getCode();
        String itemIdKey = sourceItemKey.trim();
        List<ServiceDeviceTemplateQueryModel> bindings = serviceDeviceTemplateQueryService.listBindingsByRangeTypeAndSource(
            rangeCode,
            context.typeCode(),
            context.itemTable(),
            itemIdKey
        );
        if (bindings.isEmpty()) {
            // 未配置绑定时返回空列表，由前端静默处理
            return rows;
        }
        int maxRows = DoctorStationAdviceConstants.ORDER_BIND_LIST_MAX_ROWS;
        int bound = bindings.size();
        if (bound > maxRows) {
            // 超出上限时截断，防止异常配置拖垮接口
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
            if (binding.getDevActId() == null || StringUtils.isEmpty(binding.getDevActTable())) {
                // 模板目标主键或表名缺失时跳过，避免返回不可落库行
                continue;
            }
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
            rows.add(new BindAssemblyRow(row, binding.getDevActTable().trim()));
        }
        return rows;
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
            // 诊疗绑定来源键为 wkf_activity_definition.id 字符串
            return Optional.of(new BindQueryContext(
                ServiceDeviceTemplateTypeCode.ACTIVITY.getCode(),
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                false
            ));
        }
        return Optional.empty();
    }

    /**
     * 解析绑定来源项展示名：用法取术语 label，诊疗取活动定义名称。
     *
     * @param context           绑定上下文
     * @param sourceItemKey     来源键
     * @param methodLabelByCode 用法 code → 展示名
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
            List<com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition> activityRows =
                activityDefinitionQueryService.findAllByIds(List.of(activityId));
            if (!activityRows.isEmpty() && activityRows.get(0) != null) {
                return activityRows.get(0).getName();
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
     * 绑定目标去重键：table + 分隔符 + id。
     *
     * @param table    品项表
     * @param devActId 目标 id
     * @return table + 分隔符 + id
     */
    private static String buildTargetDedupeKey(String table, Long devActId) {
        return table + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR + devActId;
    }

    /**
     * 解析绑定目标展示名称。
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
     * 解析绑定目标产品 DTO（供 IF-DOR-23 回填规格等）。
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

    /** 模板绑定类型与来源表解析上下文。 */
    private record BindQueryContext(String typeCode, String itemTable, boolean sourceIsMethodCode) {
    }

    /** IF-DOR-12 行 DTO 与目标产品表名成对，供 IF-DOR-23 回填 {@code targetProductTableName}。 */
    private record BindAssemblyRow(DoctorStationAdviceOrderBindRowDto row, String targetProductTableName) {
    }
}
