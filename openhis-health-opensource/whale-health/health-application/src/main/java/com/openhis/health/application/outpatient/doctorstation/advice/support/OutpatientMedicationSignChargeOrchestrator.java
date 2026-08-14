/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.support;

import com.openhis.health.application.shared.advice.OrderChargeDraftAssembler;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊医生站药品签发后 FIFO 发放与分段挂费编排（IF-DOR-15 / IF-DOR-37 药线共用）。
 *
 * <p>西药 Tab 与中医 Tab 在「sign → void 暂存费 → FIFO 发放 → 分段 ChargeItem 草稿 → PLANNED」须同构；
 * 差异仅处方号来源（全就诊分方 vs prescriptionGroupKey），本 Support 不含分方逻辑。</p>
 *
 * <p>分段挂费单价必须按 FIFO 分段 {@code lotNumber} 匹配 {@code LOT_NUMBER_PRICE}
 *（{@link ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft}）；
 * <strong>禁止</strong>对药耗跨批号 {@code priority}/id 择优落账。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientMedicationSignChargeOrchestrator {

    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 单次签发批次内挂费读侧缓存：同产品键复用定价行列表与产品展示，避免循环 N+1。
     *
     * <p>仅作用于一次 AppService 签发调用；调用方须新建实例，禁止跨请求复用。
     * 价行按产品键缓存<strong>全量 ACTIVE 行</strong>，选价时再按 FIFO 批号匹配。</p>
     */
    public static final class SignChargeReadCache {

        /** 产品复合键 → 已加载的定价行列表（可为空列表表示查过无行）。 */
        private final Map<String, List<ChargeItemDefinition>> priceLinesByProductKey = new HashMap<>();

        /** 已尝试加载价行的产品键（区分「未查」与「查过无行」）。 */
        private final Set<String> priceResolvedKeys = new LinkedHashSet<>();

        /** 产品复合键 → 产品展示 DTO。 */
        private final Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();
    }

    /**
     * 单条药品医嘱签发后的 FIFO 发放与 PLANNED 费用编排（与 IF-DOR-15 药线逐步对齐）。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param accountId   挂账账户（按行费用性质解析后的 account_id）
     * @param orgId       开立机构
     * @param operatorId  操作人
     * @param medReqId    药品申请主键（已 sign）
     * @param fifoCtx     FIFO 只读上下文
     * @param readCache   批内挂费读缓存；null 时退化为逐条查价（语义不变）
     * @throws BusinessException FIFO 分段与数量不一致、定价缺失或发放失败
     */
    public void replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medReqId,
        MedicationRequestFifoSignContext fifoCtx,
        SignChargeReadCache readCache
    ) {
        // Cmd：作废仍锚定在医嘱请求上的暂存费用，避免签发后双挂
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
            encounterId, TableNameConstants.MED_MEDICATION_REQUEST, List.of(medReqId));
        // Pre：按模板与库存 FIFO 切分发放数量（库存写后占用变化，禁止跨行复用分段）
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments =
            doctorStationAdviceFifoAllocationAssembler.allocateMedicationFifo(
                orgId, fifoCtx.getEncounterId(), fifoCtx.getMedicationId(), fifoCtx.getCatalogCategoryCode(),
                fifoCtx.getMethodCode(), fifoCtx.getUnitCode(), fifoCtx.getQuantity());
        ReplaceFifoMedicationDispenseDraftsCommand fifoCmd = new ReplaceFifoMedicationDispenseDraftsCommand();
        fifoCmd.setOperatorId(operatorId);
        fifoCmd.setMedReqId(fifoCtx.getMedReqId());
        fifoCmd.setMedicationId(fifoCtx.getMedicationId());
        fifoCmd.setEncounterId(fifoCtx.getEncounterId());
        fifoCmd.setPatientId(fifoCtx.getPatientId());
        fifoCmd.setUnitCode(fifoCtx.getUnitCode());
        fifoCmd.setDispenseEnum(MedicationDispenseType.OUTPATIENT);
        fifoCmd.setSegments(segments);
        // Cmd：替换为签发后 FIFO 发放草稿分段
        List<Long> dispenseIds = medicationDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoCmd);
        List<BigDecimal> segmentQuantities = new ArrayList<>();
        List<String> segmentLotNumbers = new ArrayList<>();
        for (ReplaceFifoMedicationDispenseDraftsCommand.Segment segment : segments) {
            segmentQuantities.add(segment.getQuantity());
            segmentLotNumbers.add(segment.getLotNumber());
        }
        // Cmd：按发放分段重建费用草稿（收银仅见 PLANNED，须先落 DRAFT 再升位；单价跟 FIFO 批号）
        createDispenseSegmentChargeDrafts(
            encounterId, patientId, accountId, orgId, operatorId,
            fifoCtx.getMedReqId(),
            TableNameConstants.MED_MEDICATION_REQUEST,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            fifoCtx.getMedicationId(),
            TableNameConstants.MED_MEDICATION_DISPENSE,
            dispenseIds,
            segmentQuantities,
            segmentLotNumbers,
            fifoCtx.getUnitCode(),
            fifoCtx.getPrescriptionNo(),
            readCache);
        // Cmd：草稿费用升为 PLANNED，供门诊收银预结算
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
            encounterId, TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds);
    }

    /**
     * 按本批药品定义主键预取挂费价行与产品展示（一次 IN 查询，写入 {@link SignChargeReadCache}）。
     *
     * @param readCache               批内缓存；null 则忽略
     * @param medicationDefinitionIds 药品定义主键集合
     */
    public void prefetchMedicationCatalogReads(SignChargeReadCache readCache, Collection<Long> medicationDefinitionIds) {
        this.prefetchCatalogReads(readCache, TableNameConstants.MED_MEDICATION_DEFINITION, medicationDefinitionIds);
    }

    /**
     * 按本批耗材定义主键预取挂费价行与产品展示。
     *
     * @param readCache           批内缓存；null 则忽略
     * @param deviceDefinitionIds 耗材定义主键集合
     */
    public void prefetchDeviceCatalogReads(SignChargeReadCache readCache, Collection<Long> deviceDefinitionIds) {
        this.prefetchCatalogReads(readCache, TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefinitionIds);
    }

    /**
     * 按 FIFO 发放分段批量生成费用草稿；每一分段按本段批号匹配 {@code LOT_NUMBER_PRICE}。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              账户主键
     * @param orgId                  机构主键
     * @param operatorId             操作人
     * @param requestId              医嘱申请主键
     * @param requestServiceTable    医嘱请求表名
     * @param productDefinitionTable 产品定义表
     * @param productDefinitionId    产品定义主键
     * @param dispenseTable          发放表名
     * @param dispenseIds            发放分段 id 列表
     * @param segmentQuantities      各分段数量
     * @param segmentLotNumbers      各分段 FIFO 批号（与 dispenseIds 等长；落账对价键）
     * @param quantityUnit           计价单位
     * @param prescriptionNo         药品请求处方号（同方多味共享；写入 ChargeItem 供工作量按方去重）
     * @param readCache              批内挂费读缓存；null 时逐条查价
     */
    public void createDispenseSegmentChargeDrafts(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        List<Long> dispenseIds,
        List<BigDecimal> segmentQuantities,
        List<String> segmentLotNumbers,
        String quantityUnit,
        String prescriptionNo,
        SignChargeReadCache readCache
    ) {
        if (dispenseIds == null || segmentQuantities == null || segmentLotNumbers == null
            || dispenseIds.size() != segmentQuantities.size()
            || dispenseIds.size() != segmentLotNumbers.size()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "发放分段与费用草稿数量/批号不一致");
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        ProductDefinitionDisplayDto productRow = resolveProductDisplay(productDefinitionTable, productDefinitionId, readCache);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        String trimmedPrescriptionNo = null;
        if (StringUtils.isNotEmpty(prescriptionNo)) {
            trimmedPrescriptionNo = prescriptionNo.trim();
        }
        // Asm：每一发放分段一行 ChargeItem，单价必须跟本段 FIFO 批号
        for (int i = 0; i < dispenseIds.size(); i++) {
            String fifoLotNumber = segmentLotNumbers.get(i);
            ChargeItemDefinition priceLine = resolvePriceLineForFifoLot(
                productDefinitionTable, productDefinitionId, fifoLotNumber, readCache);
            if (priceLine == null) {
                String lotLabel = fifoLotNumber;
                if (StringUtils.isEmpty(lotLabel)) {
                    lotLabel = "(空批号)";
                }
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                    "未配置符合开立规则的收费定价（药品/耗材须批号售价 LOT_NUMBER_PRICE，FIFO 批号="
                        + lotLabel
                        + "）。请先维护该批号收费项定价后再签发医嘱。");
            }
            CreateChargeItemCommand.Item draftItem =
                OrderChargeDraftAssembler.toCreateChargeItemDraftItemForFifoDispenseSegment(
                    encounterId,
                    patientId,
                    accountId,
                    orgId,
                    requestId,
                    requestServiceTable,
                    productDefinitionTable,
                    productDefinitionId,
                    dispenseTable,
                    dispenseIds.get(i),
                    priceLine,
                    segmentQuantities.get(i),
                    quantityUnit,
                    productRow);
            // Asm：药品处方号落收费行，同方多味共享，工作量按方计 1
            if (trimmedPrescriptionNo != null) {
                draftItem.setPrescriptionNo(trimmedPrescriptionNo);
            }
            draftItems.add(draftItem);
        }
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(draftItems);
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    /**
     * 预取指定产品表下多条定义的价行列表与展示 DTO，写入批缓存（不在此步跨批号择优）。
     *
     * @param readCache              批内缓存
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionIds   产品定义主键
     */
    private void prefetchCatalogReads(
        SignChargeReadCache readCache,
        String productDefinitionTable,
        Collection<Long> productDefinitionIds
    ) {
        if (readCache == null || productDefinitionIds == null || productDefinitionIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long productDefinitionId : productDefinitionIds) {
            if (productDefinitionId != null) {
                uniqueIds.add(productDefinitionId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return;
        }
        // Pre：一次查价，按 instanceId 分组缓存全量行；选价留到各 FIFO 批号匹配时
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(productDefinitionTable);
        priceQuery.setInstanceIds(uniqueIds);
        List<ChargeItemDefinition> allPriceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        Map<Long, List<ChargeItemDefinition>> linesByInstanceId = new HashMap<>();
        if (allPriceLines != null) {
            for (ChargeItemDefinition line : allPriceLines) {
                if (line == null || line.getInstanceId() == null) {
                    continue;
                }
                List<ChargeItemDefinition> bucket = linesByInstanceId.get(line.getInstanceId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    linesByInstanceId.put(line.getInstanceId(), bucket);
                }
                bucket.add(line);
            }
        }
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (Long productDefinitionId : uniqueIds) {
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(productDefinitionTable, productDefinitionId);
            List<ChargeItemDefinition> linesForInstance = linesByInstanceId.get(productDefinitionId);
            if (linesForInstance == null) {
                linesForInstance = List.of();
            }
            readCache.priceLinesByProductKey.put(productKey, linesForInstance);
            readCache.priceResolvedKeys.add(productKey);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(productDefinitionTable);
            lookup.setProductDefinitionId(productDefinitionId);
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        if (loaded != null) {
            readCache.productByKey.putAll(loaded);
        }
    }

    /**
     * 按 FIFO 批号解析挂费价行：优先批缓存中的价行列表，再
     * {@link ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft}。
     *
     * @param instanceTable  实例表名
     * @param instanceId     实例 id
     * @param fifoLotNumber  FIFO 分段批号
     * @param readCache      批内缓存
     * @return 价行；无法按批号命中且无兜底时 null
     */
    private ChargeItemDefinition resolvePriceLineForFifoLot(
        String instanceTable,
        Long instanceId,
        String fifoLotNumber,
        SignChargeReadCache readCache) {
        if (instanceId == null) {
            return null;
        }
        List<ChargeItemDefinition> priceLines = loadPriceLines(instanceTable, instanceId, readCache);
        // Pre：药耗落账必须跟 FIFO 批号；与护士执行挂费同口径
        return clinicalCatalogPriceResolver.selectExecutionPriceLineForChargeDraft(
            instanceTable, priceLines, fifoLotNumber);
    }

    /**
     * 加载产品定价行列表：优先批缓存，未命中再单条查。
     *
     * @param instanceTable 实例表名
     * @param instanceId    实例 id
     * @param readCache     批内缓存
     * @return 定价行列表；无行时为空列表
     */
    private List<ChargeItemDefinition> loadPriceLines(
        String instanceTable,
        Long instanceId,
        SignChargeReadCache readCache) {
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(instanceTable, instanceId);
        if (readCache != null && readCache.priceResolvedKeys.contains(productKey)) {
            List<ChargeItemDefinition> cached = readCache.priceLinesByProductKey.get(productKey);
            if (cached == null) {
                return List.of();
            }
            return cached;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        if (priceLines == null) {
            priceLines = List.of();
        }
        if (readCache != null) {
            readCache.priceLinesByProductKey.put(productKey, priceLines);
            readCache.priceResolvedKeys.add(productKey);
        }
        return priceLines;
    }

    /**
     * 解析产品展示 DTO：优先批缓存，未命中再单条加载。
     *
     * @param table     产品表名
     * @param id        产品 id
     * @param readCache 批内缓存
     * @return 展示 DTO
     * @throws BusinessException 未找到产品定义
     */
    private ProductDefinitionDisplayDto resolveProductDisplay(String table, Long id, SignChargeReadCache readCache) {
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        if (readCache != null) {
            ProductDefinitionDisplayDto cached = readCache.productByKey.get(key);
            if (cached != null) {
                return cached;
            }
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "未找到药品产品定义行");
        }
        if (readCache != null) {
            readCache.productByKey.put(key, row);
        }
        return row;
    }
}
