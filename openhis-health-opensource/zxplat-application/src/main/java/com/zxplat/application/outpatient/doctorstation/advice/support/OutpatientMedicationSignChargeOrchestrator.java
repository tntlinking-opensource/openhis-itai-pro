/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import com.zxplat.application.shared.advice.OrderChargeDraftAssembler;
import com.zxplat.application.shared.advice.OrderChargePricingAssembler;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.OutpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *
 * <p><strong>BR-ORD-033</strong>：写 ChargeItem 前须核对发放行落库批号与 FIFO 分段一致，
 * 禁止「定价有批、发放无批」进入收银（与 **BR-OPC-023** / 护士站执行批号门禁衔接）。</p>
 *
 * <p><strong>BR-ORD-035</strong>：FIFO 拆为多段时，写发放前须医生确认各批数量与金额（EX-ORD-310），
 * 避免开立预览首批单价与签发后合计不一致却无感落账。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientMedicationSignChargeOrchestrator {

    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
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
     * @param encounterId               就诊主键
     * @param patientId                 患者主键
     * @param accountId                 挂账账户（按行费用性质解析后的 account_id）
     * @param orgId                     开立机构
     * @param operatorId                操作人
     * @param medReqId                  药品申请主键（已 sign）
     * @param fifoCtx                   FIFO 只读上下文
     * @param readCache                 批内挂费读缓存；null 时退化为逐条查价（语义不变）
     * @param confirmCrossLotSkinTest   医生已确认皮试药可跨批（BR-ORD-034）；非皮试忽略
     * @param confirmCrossLotPricing    医生已确认跨批次分段金额（BR-ORD-035）；单批忽略
     * @throws BusinessException FIFO 分段与数量不一致、定价缺失、需确认跨批或发放失败
     */
    public void replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medReqId,
        MedicationRequestFifoSignContext fifoCtx,
        SignChargeReadCache readCache,
        boolean confirmCrossLotSkinTest,
        boolean confirmCrossLotPricing
    ) {
        // Gate：挂费就诊/患者必须与医嘱 FIFO 上下文一致，防止切患者后「新就诊 + 旧医嘱」串挂费
        if (fifoCtx == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
        }
        if (!java.util.Objects.equals(encounterId, fifoCtx.getEncounterId())
            || !java.util.Objects.equals(patientId, fifoCtx.getPatientId())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "处方不属于当前患者/就诊，请刷新后重试");
        }
        if (medReqId != null && fifoCtx.getMedReqId() != null
            && !java.util.Objects.equals(medReqId, fifoCtx.getMedReqId())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "处方不属于当前患者/就诊，请刷新后重试");
        }
        // Cmd：作废仍锚定在医嘱请求上的暂存费用，避免签发后双挂
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
            encounterId, TableNameConstants.MED_MEDICATION_REQUEST, List.of(medReqId));
        // Pre：门诊皮试药优先单批 FIFO（BR-ORD-034）；非皮试或已确认跨批走普通切分
        boolean outpatientSkinTestPreferSingleLot = Boolean.TRUE.equals(fifoCtx.getSkinTestFlag());
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments =
            doctorStationAdviceFifoAllocationAssembler.allocateMedicationFifo(
                orgId,
                fifoCtx.getEncounterId(),
                fifoCtx.getMedicationId(),
                fifoCtx.getCatalogCategoryCode(),
                fifoCtx.getMethodCode(),
                fifoCtx.getUnitCode(),
                fifoCtx.getQuantity(),
                outpatientSkinTestPreferSingleLot,
                confirmCrossLotSkinTest);
        List<BigDecimal> segmentQuantities = new ArrayList<>();
        List<String> segmentLotNumbers = new ArrayList<>();
        for (ReplaceFifoMedicationDispenseDraftsCommand.Segment segment : segments) {
            segmentQuantities.add(segment.getQuantity());
            segmentLotNumbers.add(segment.getLotNumber());
        }
        // Gate（BR-ORD-035）：跨批号（去重批号 ≥ 2）须医生确认各批数量与金额后再落发放
        this.assertCrossLotPricingConfirmedOrThrow(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            fifoCtx.getMedicationId(),
            fifoCtx.getUnitCode(),
            segmentQuantities,
            segmentLotNumbers,
            readCache,
            confirmCrossLotPricing);
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
     * 单条药品医嘱签发后的 FIFO 发放与 PLANNED 费用编排（皮试跨批默认未确认；跨批金额默认已确认）。
     *
     * <p>中医 Tab 等未接 EX-ORD-310 弹窗的调用方走本重载，跨批金额确认视为已通过，避免无前端确认链路被阻断。</p>
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param accountId   挂账账户
     * @param orgId       开立机构
     * @param operatorId  操作人
     * @param medReqId    药品申请主键（已 sign）
     * @param fifoCtx     FIFO 只读上下文
     * @param readCache   批内挂费读缓存
     * @throws BusinessException FIFO/定价/发放失败
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
        // 中药等未传确认标志的调用方：跨批金额确认默认 true，避免中医 Tab 无弹窗链路被 EX-ORD-310 阻断
        replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
            encounterId, patientId, accountId, orgId, operatorId, medReqId, fifoCtx, readCache, false, true);
    }

    /**
     * Gate（BR-ORD-035）：FIFO 涉及 2 个及以上不同批号时组装各批数量/金额文案，未确认则抛 EX-ORD-310。
     *
     * <p>同批号多库位拆成多段发放（分段数 &gt; 1 但批号唯一）<strong>不</strong>弹窗，与单批签发体验一致。</p>
     *
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionId    产品定义主键
     * @param quantityUnit           开立单位 code
     * @param segmentQuantities      各段数量（开立单位）
     * @param segmentLotNumbers      各段批号
     * @param readCache              批内挂费读缓存
     * @param confirmCrossLotPricing 医生已确认跨批金额
     * @throws BusinessException 跨批号且未确认，或分段定价缺失
     */
    public void assertCrossLotPricingConfirmedOrThrow(
        String productDefinitionTable,
        Long productDefinitionId,
        String quantityUnit,
        List<BigDecimal> segmentQuantities,
        List<String> segmentLotNumbers,
        SignChargeReadCache readCache,
        boolean confirmCrossLotPricing
    ) {
        // Gate：已确认则放行
        if (confirmCrossLotPricing) {
            return;
        }
        if (segmentQuantities == null || segmentLotNumbers == null
            || segmentQuantities.isEmpty()
            || segmentQuantities.size() != segmentLotNumbers.size()) {
            if (segmentQuantities != null && segmentLotNumbers != null
                && segmentQuantities.size() != segmentLotNumbers.size()) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "发放分段数量与批号不一致");
            }
            return;
        }
        // Gate：仅当去重批号 ≥ 2 才视为跨批次；同批多段（多库位）不拦截
        List<String> distinctLotsInFifoOrder = collectDistinctNonEmptyLotNumbersInOrder(segmentLotNumbers);
        if (distinctLotsInFifoOrder.size() <= 1) {
            return;
        }
        ProductDefinitionDisplayDto productRow =
            this.resolveProductDisplay(productDefinitionTable, productDefinitionId, readCache);
        String productName = productRow.getProductName();
        if (StringUtils.isEmpty(productName)) {
            productName = "未命名药品/耗材";
        }
        String unitName = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayNameFromProduct(
            productRow, quantityUnit);
        if (StringUtils.isEmpty(unitName)) {
            unitName = quantityUnit;
        }
        if (StringUtils.isEmpty(unitName)) {
            unitName = "";
        }
        // Pre：按批号汇总各段数量与行金额（FIFO 同批多段合并为一行展示）
        Map<String, BigDecimal> quantityByLot = new LinkedHashMap<>();
        Map<String, BigDecimal> lineTotalByLot = new LinkedHashMap<>();
        for (int i = 0; i < segmentQuantities.size(); i++) {
            BigDecimal qty = segmentQuantities.get(i);
            String lotNumber = segmentLotNumbers.get(i);
            // Gate：分段批号禁止空，与落账口径一致
            if (StringUtils.isEmpty(lotNumber)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "FIFO 分段批号不能为空，禁止签发");
            }
            String normalizedLot = lotNumber.trim();
            ChargeItemDefinition priceLine = this.resolvePriceLineForFifoLot(
                productDefinitionTable, productDefinitionId, normalizedLot, readCache);
            if (priceLine == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                    "未配置符合开立规则的收费定价（药品/耗材须批号售价 LOT_NUMBER_PRICE，FIFO 批号="
                        + normalizedLot
                        + "）。请先维护该批号收费项定价后再签发医嘱。");
            }
            BigDecimal lineTotal = OrderChargePricingAssembler.computeOrderChargeLineTotal(
                productRow, priceLine, qty, quantityUnit);
            BigDecimal prevQty = quantityByLot.get(normalizedLot);
            if (prevQty == null) {
                prevQty = BigDecimal.ZERO;
            }
            quantityByLot.put(normalizedLot, prevQty.add(qty));
            BigDecimal prevTotal = lineTotalByLot.get(normalizedLot);
            if (prevTotal == null) {
                prevTotal = BigDecimal.ZERO;
            }
            lineTotalByLot.put(normalizedLot, MonetaryAmountSupport.sumThenCalc(List.of(prevTotal, lineTotal)));
        }
        List<BigDecimal> lineTotalsCalc = new ArrayList<>();
        List<String> segmentParts = new ArrayList<>();
        int lotIndex = 0;
        for (String lotKey : distinctLotsInFifoOrder) {
            lotIndex++;
            BigDecimal lotQty = quantityByLot.get(lotKey);
            BigDecimal lotTotal = lineTotalByLot.get(lotKey);
            if (lotTotal == null) {
                lotTotal = BigDecimal.ZERO;
            }
            lineTotalsCalc.add(lotTotal);
            String qtyLabel = this.formatQuantityForConfirm(lotQty);
            String amountLabel = MonetaryAmountSupport.scaleDisplay(lotTotal).toPlainString();
            segmentParts.add("第" + lotIndex + "批 " + qtyLabel + unitName + " " + amountLabel + "元");
        }
        // Asm：合计先按 6 位代数加总再展示刻度，与 BR-MON-001 结算口径一致
        BigDecimal grandTotalCalc = MonetaryAmountSupport.sumThenCalc(lineTotalsCalc);
        String grandTotalLabel = MonetaryAmountSupport.scaleDisplay(grandTotalCalc).toPlainString();
        String segmentDetail = String.join("；", segmentParts);
        String message = String.format(
            DoctorStationAdviceConstants.MSG_SIGN_CROSS_LOT_PRICING_CONFIRM,
            productName,
            segmentDetail,
            grandTotalLabel);
        throw new BusinessException(DoctorStationAdviceConstants.EX_ORD_310, message);
    }

    /**
     * 按 FIFO 分段顺序收集非空批号的去重列表（同批多段只保留首次出现顺序）。
     *
     * @param segmentLotNumbers 各段批号
     * @return 去重批号列表；无有效批号时为空列表
     */
    private static List<String> collectDistinctNonEmptyLotNumbersInOrder(List<String> segmentLotNumbers) {
        List<String> ordered = new ArrayList<>();
        if (segmentLotNumbers == null || segmentLotNumbers.isEmpty()) {
            return ordered;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String lotNumber : segmentLotNumbers) {
            if (StringUtils.isEmpty(lotNumber)) {
                continue;
            }
            String normalized = lotNumber.trim();
            if (seen.add(normalized)) {
                ordered.add(normalized);
            }
        }
        return ordered;
    }

    /**
     * 将数量格式化为确认弹窗可读文案（去掉尾随零）。
     *
     * @param quantity 开立单位数量
     * @return 非空数量文案
     */
    private String formatQuantityForConfirm(BigDecimal quantity) {
        if (quantity == null) {
            return "0";
        }
        return quantity.stripTrailingZeros().toPlainString();
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
     * <p>挂费前执行 **BR-ORD-033**：发放行落库批号须与分段批号一致且非空/非占位，否则拒绝写 ChargeItem。</p>
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
     * @param prescriptionNo         药品请求处方号（调用方仍传入供 FIFO 上下文；收费行<strong>不</strong>预写，结算再抄快照）
     * @param readCache              批内挂费读缓存；null 时逐条查价
     * @throws BusinessException 分段长度不一致、批号缺失/漂移或定价缺失时
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
        // Gate：挂费前核对发放行已落库真实批号，且与 FIFO 分段一致（防「收费有批、发放无批」）
        this.assertPersistedDispenseLotsMatchFifoSegments(dispenseTable, dispenseIds, segmentLotNumbers);
        ProductDefinitionDisplayDto productRow = resolveProductDisplay(productDefinitionTable, productDefinitionId, readCache);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        // Pre：prescriptionNo 参数仅兼容调用方签名，不写入收费行（真源在药品请求；结算再抄快照）。
        // Asm：每一发放分段一行 ChargeItem，单价必须跟本段 FIFO 批号
        for (int i = 0; i < dispenseIds.size(); i++) {
            String fifoLotNumber = segmentLotNumbers.get(i);
            // Gate：分段批号禁止空，避免定价落到错误价行
            if (StringUtils.isEmpty(fifoLotNumber)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "FIFO 分段批号不能为空，禁止挂费");
            }
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
            // Asm：药品收费行不预写处方号（真源在 MedicationRequest；结算时再抄快照，避免重分方漂移）。
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

    /**
     * Gate：挂费前核对发放行落库批号与 FIFO 分段批号一致。
     *
     * <p>挂费单价取自内存分段批号；若发放行批号空或漂移，会出现「已收费、护士执行报批号缺失」。
     * 本门禁在写 ChargeItem 之前打断，逼医生重新签发。</p>
     *
     * @param dispenseTable      发放表名（药品/耗材）
     * @param dispenseIds        发放主键（与分段等长）
     * @param segmentLotNumbers  FIFO 分段批号
     * @throws BusinessException 发放缺失、批号空、占位批号或与分段不一致时
     */
    private void assertPersistedDispenseLotsMatchFifoSegments(
        String dispenseTable,
        List<Long> dispenseIds,
        List<String> segmentLotNumbers) {
        for (int i = 0; i < dispenseIds.size(); i++) {
            Long dispenseId = dispenseIds.get(i);
            String expectedLot = segmentLotNumbers.get(i);
            // Gate：分段批号本身禁止空
            if (StringUtils.isEmpty(expectedLot)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "FIFO 分段批号不能为空，禁止挂费");
            }
            String expectedTrim = expectedLot.trim();
            // Gate：占位批号不可进入已收费链路
            if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(expectedTrim)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "FIFO 分段批号为占位值，禁止挂费");
            }
            String actualLot = null;
            // Pre：按发放表分流装载批号（仅本挂费编排读 QS，不跨聚合写）
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(dispenseTable)) {
                DeviceDispense deviceDispense = this.deviceDispenseQueryService.findById(dispenseId);
                // Gate：发放行须已落库
                if (deviceDispense == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND,
                        "耗材发放分段不存在，禁止挂费");
                }
                actualLot = deviceDispense.getLotNumber();
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(dispenseTable)) {
                MedicationDispense medicationDispense = this.medicationDispenseQueryService.findById(dispenseId);
                // Gate：发放行须已落库
                if (medicationDispense == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_DISPENSE_NOT_FOUND,
                        "药品发放分段不存在，禁止挂费");
                }
                actualLot = medicationDispense.getLotNumber();
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "不支持的发放表，禁止挂费: " + dispenseTable);
            }
            // Gate：落库批号须与 FIFO 分段完全一致
            if (StringUtils.isEmpty(actualLot) || !expectedTrim.equals(actualLot.trim())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "发放批号与 FIFO 挂费批号不一致或缺失，请重新签发后再收费");
            }
        }
    }
}
