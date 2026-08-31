/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper.DoctorStationAdviceFifoAllocationResult;
import com.openhis.health.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper.DoctorStationAdviceFifoSegmentDraft;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊医生站签发相位：按模板库位优先序 + 批次效期/入库序（与目录可用量查询一致）做 FIFO 拆分，产出发放草稿分段命令行。
 *
 * <p>口径对齐 IF-DOR-11：仅在模板给出的候选库位集合上与库存求交，并扣减同就诊未完结发放占用。
 * 跨批次切分以医嘱开立单位为最小粒度：拆零开立可部分切分，盒等整包装开立每段须为整数个开立单位。
 * 门诊皮试药（BR-ORD-034）优先整量落在 FIFO 序第一个够量的单批；无一单批够量时须医生确认后方可跨批。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationAdviceFifoAllocationAssembler {

    /** 取药科室模板候选库位解析。 */
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;
    /** 库存可用行读侧（FIFO 批次来源）。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 药品发放占用读侧（预减同就诊未完结发放）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 耗材发放占用读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 产品定义批量加载（单位换算所需读模型）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 药品：按 FIFO 产出签发后草稿发放分段。
     *
     * @param orgId                  开立/接诊机构，用于模板解析
     * @param encounterId            就诊主键（签发命令校验；预减为候选库房全局口径）
     * @param medicationDefinitionId 药品定义主键
     * @param categoryCode           药品/耗材目录 categoryCode（模板 distribution_category_code；空则回落 DEFAULT）
     * @param methodCode             给药途径编码（模板 calcScore 维度；可空）
     * @param businessUnitCode       医嘱开立数量单位编码（与 {@code MedicationRequest#unitCode} 一致）
     * @param totalQuantity          医嘱开立总量（业务单位）
     * @return 有序分段列表（各段 {@code quantity} 为业务单位，与 {@code businessUnitCode} 配套）
     * @throws BusinessException 模板无候选库位、库存不足以覆盖总量或参数非法时
     */
    public List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> allocateMedicationFifo(
            Long orgId,
            Long encounterId,
            Long medicationDefinitionId,
            String categoryCode,
            String methodCode,
            String businessUnitCode,
            BigDecimal totalQuantity
    ) {
        // 默认非皮试单批优先（住院/普通门诊药线）
        return allocateMedicationFifo(
            orgId,
            encounterId,
            medicationDefinitionId,
            categoryCode,
            methodCode,
            businessUnitCode,
            totalQuantity,
            false,
            false);
    }

    /**
     * 药品 FIFO 分段（门诊皮试可单批优先，见 BR-ORD-034）。
     *
     * <p>当 {@code outpatientSkinTestPreferSingleLot=true}：按 FIFO 序找第一个单批够量的批号整量落批；
     * 无一单批够量且未确认跨批时，若多批合计够量则抛 {@link DoctorStationAdviceConstants#EX_ORD_309}；
     * 已确认或非皮试则走普通跨批 FIFO。</p>
     *
     * @param orgId                              开立/接诊机构
     * @param encounterId                        就诊主键
     * @param medicationDefinitionId             药品定义主键
     * @param categoryCode                       目录 categoryCode
     * @param methodCode                         给药途径编码
     * @param businessUnitCode                   开立单位
     * @param totalQuantity                      开立总量
     * @param outpatientSkinTestPreferSingleLot  门诊皮试药：优先单批覆盖
     * @param confirmCrossLotSkinTest            医生已确认可跨批（应对 EX-ORD-309）
     * @return 有序分段列表
     * @throws BusinessException 库存不足、需确认跨批或参数非法时
     */
    public List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> allocateMedicationFifo(
            Long orgId,
            Long encounterId,
            Long medicationDefinitionId,
            String categoryCode,
            String methodCode,
            String businessUnitCode,
            BigDecimal totalQuantity,
            boolean outpatientSkinTestPreferSingleLot,
            boolean confirmCrossLotSkinTest
    ) {
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配缺少 orgId");
        }
        if (encounterId == null || medicationDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配缺少 encounterId/medicationDefinitionId");
        }
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配数量必须大于 0");
        }
        ProductDefinitionDisplayDto productRow = DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
                productDefinitionDisplayTerminologyAssembler,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationDefinitionId);
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
                orgId,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                distributionCategory,
                medicationDefinitionId,
                methodCode,
                null,
                null);
        if (candidateLocs.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "未解析到候选发放库位，无法按 FIFO 拆分发药");
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.MED_MEDICATION_DEFINITION, List.of(medicationDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildMedicationOccupancy(candidateLocs, medicationDefinitionId, productRow);
        // 门诊皮试：FIFO 序优先整量落在第一个够量的单批，避免一次就诊被拆成多批逼出多次皮试
        if (outpatientSkinTestPreferSingleLot) {
            DoctorStationAdviceFifoSegmentDraft singleLotDraft =
                DoctorStationAdviceInventoryUnitQuantityHelper.tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit(
                    physical,
                    occ,
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    productRow,
                    businessUnitCode,
                    totalQuantity);
            // 已找到够量单批：整量落该批，不再跨批
            if (singleLotDraft != null) {
                return toMedicationDispenseSegments(List.of(singleLotDraft));
            }
        }
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                physical, occ, TableNameConstants.MED_MEDICATION_DEFINITION, productRow, businessUnitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 拒绝签发时点名药品，便于医生直接改量或换药
            throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
                    String.format(
                            DoctorStationAdviceConstants.MSG_SIGN_FIFO_INSUFFICIENT_STOCK_FOR_ADVICE,
                            resolveFifoProductDisplayName(productRow, "药品")));
        }
        List<DoctorStationAdviceFifoSegmentDraft> segments = fifoResult.getSegments();
        // 门诊皮试：无一单批够量但多批合计够量 → 未确认则提示医生（确认后本批允许跨批）
        if (outpatientSkinTestPreferSingleLot
            && !confirmCrossLotSkinTest
            && segments != null
            && segments.size() > 1) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_ORD_309,
                String.format(
                    DoctorStationAdviceConstants.MSG_SIGN_SKIN_TEST_CROSS_LOT_CONFIRM,
                    resolveFifoProductDisplayName(productRow, "药品")));
        }
        return toMedicationDispenseSegments(segments);
    }

    /**
     * IF-DOR-14 暂存门控：门诊皮试药无一单批够量且须跨批时，未确认则抛 {@link DoctorStationAdviceConstants#EX_ORD_309}。
     *
     * <p>只读试算，不加库存行锁；签发相位仍走 {@link #allocateMedicationFifo}。</p>
     *
     * @param orgId                      开立机构
     * @param encounterId                就诊主键
     * @param medicationDefinitionId     药品定义主键
     * @param categoryCode               目录 categoryCode
     * @param methodCode                 给药途径
     * @param businessUnitCode           开立单位
     * @param totalQuantity              开立总量
     * @param confirmCrossLotSkinTest    医生已确认可跨批
     * @throws BusinessException 须确认跨批或参数非法时
     */
    public void assertOutpatientSkinTestCrossLotConfirmedOrThrow(
        Long orgId,
        Long encounterId,
        Long medicationDefinitionId,
        String categoryCode,
        String methodCode,
        String businessUnitCode,
        BigDecimal totalQuantity,
        boolean confirmCrossLotSkinTest
    ) {
        // 已确认：保存/签发均不再弹窗
        if (confirmCrossLotSkinTest) {
            return;
        }
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "皮试 FIFO 试算缺少 orgId");
        }
        if (encounterId == null || medicationDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "皮试 FIFO 试算缺少 encounterId/medicationDefinitionId");
        }
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "皮试 FIFO 试算数量必须大于 0");
        }
        ProductDefinitionDisplayDto productRow = DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
            productDefinitionDisplayTerminologyAssembler,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            medicationDefinitionId);
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
            orgId,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            distributionCategory,
            medicationDefinitionId,
            methodCode,
            null,
            null);
        if (candidateLocs.isEmpty()) {
            return;
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.MED_MEDICATION_DEFINITION, List.of(medicationDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        Map<String, BigDecimal> occ = buildMedicationOccupancy(candidateLocs, medicationDefinitionId, productRow);
        DoctorStationAdviceFifoSegmentDraft singleLotDraft =
            DoctorStationAdviceInventoryUnitQuantityHelper.tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit(
                physical,
                occ,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                productRow,
                businessUnitCode,
                totalQuantity);
        if (singleLotDraft != null) {
            return;
        }
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
            physical, occ, TableNameConstants.MED_MEDICATION_DEFINITION, productRow, businessUnitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        List<DoctorStationAdviceFifoSegmentDraft> segments = fifoResult.getSegments();
        if (segments != null && segments.size() > 1) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_ORD_309,
                String.format(
                    DoctorStationAdviceConstants.MSG_SIGN_SKIN_TEST_CROSS_LOT_CONFIRM,
                    resolveFifoProductDisplayName(productRow, "药品")));
        }
    }

    /**
     * 解析药品签发 FIFO 的主发放库位（首段库位），供门诊处方分方 P1 使用。
     *
     * @param orgId                  开立机构
     * @param encounterId            就诊主键
     * @param medicationDefinitionId 药品定义主键
     * @param categoryCode           药品目录 categoryCode
     * @param methodCode             给药途径编码
     * @param businessUnitCode       开立单位
     * @param totalQuantity          开立总量
     * @return 首段发放库位；无法分配时返回 null
     */
    public Long resolvePrimaryMedicationDispenseLocationId(
        Long orgId,
        Long encounterId,
        Long medicationDefinitionId,
        String categoryCode,
        String methodCode,
        String businessUnitCode,
        BigDecimal totalQuantity
    ) {
        // 默认非皮试口径
        return resolvePrimaryMedicationDispenseLocationId(
            orgId,
            encounterId,
            medicationDefinitionId,
            categoryCode,
            methodCode,
            businessUnitCode,
            totalQuantity,
            false);
    }

    /**
     * 解析药品签发 FIFO 主发放库位（皮试药优先单批落点库位，见 BR-ORD-034）。
     *
     * <p>分方试算不抛 EX-ORD-309：皮试无一单批够量时按已确认跨批走普通 FIFO 取首段库位。</p>
     *
     * @param orgId                  开立机构
     * @param encounterId            就诊主键
     * @param medicationDefinitionId 药品定义主键
     * @param categoryCode           药品目录 categoryCode
     * @param methodCode             给药途径编码
     * @param businessUnitCode       开立单位
     * @param totalQuantity          开立总量
     * @param outpatientSkinTestPreferSingleLot 门诊皮试药优先单批
     * @return 首段发放库位；无法分配时返回 null
     */
    public Long resolvePrimaryMedicationDispenseLocationId(
        Long orgId,
        Long encounterId,
        Long medicationDefinitionId,
        String categoryCode,
        String methodCode,
        String businessUnitCode,
        BigDecimal totalQuantity,
        boolean outpatientSkinTestPreferSingleLot
    ) {
        try {
            // 分方仅需库位：皮试也按「可跨批」试算，避免 EX-ORD-309 打断分方
            List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments = allocateMedicationFifo(
                orgId,
                encounterId,
                medicationDefinitionId,
                categoryCode,
                methodCode,
                businessUnitCode,
                totalQuantity,
                outpatientSkinTestPreferSingleLot,
                true);
            // 无可用分段时不阻塞分方，库位留空由后续装箱按 null 分区
            if (segments == null || segments.isEmpty()) {
                return null;
            }
            ReplaceFifoMedicationDispenseDraftsCommand.Segment head = segments.get(0);
            if (head == null) {
                return null;
            }
            return head.getLocationId();
        } catch (BusinessException ex) {
            // 库存不足等业务拒绝时，分方仍继续，主库位为空
            return null;
        }
    }

    /**
     * FIFO 草稿分段 → 药品发放替换命令分段。
     *
     * <p>每段须带真实库存批号（对齐 **BR-ORD-033** / 药品 FIFO 签发）；空批号拒绝进入签发与挂费。</p>
     *
     * @param drafts 分配结果
     * @return 命令分段列表
     * @throws BusinessException 任一段批号为空时
     */
    private static List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> toMedicationDispenseSegments(
            List<DoctorStationAdviceFifoSegmentDraft> drafts
    ) {
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments = new ArrayList<>();
        // Gate：无草稿则返回空列表
        if (drafts == null) {
            return segments;
        }
        for (DoctorStationAdviceFifoSegmentDraft draft : drafts) {
            // Skip：空草稿不入分段
            if (draft == null) {
                continue;
            }
            // Gate：库存 FIFO 段须带真实批号，禁止空批进入签发/挂费
            if (StringUtils.isEmpty(draft.getLotNumber())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                    "库存可用行缺少批号，无法按 FIFO 签发");
            }
            ReplaceFifoMedicationDispenseDraftsCommand.Segment segment = new ReplaceFifoMedicationDispenseDraftsCommand.Segment();
            segment.setLocationId(draft.getLocationId());
            segment.setLotNumber(draft.getLotNumber().trim());
            segment.setQuantity(draft.getQuantity());
            segments.add(segment);
        }
        return segments;
    }

    /**
     * 耗材：与药品同构的 FIFO 分段。
     *
     * @param orgId              机构主键
     * @param encounterId        就诊主键
     * @param deviceDefinitionId 耗材定义主键
     * @param categoryCode       耗材目录 categoryCode（模板 distribution_category_code；空则回落 DEFAULT）
     * @param businessUnitCode   医嘱开立数量单位编码
     * @param totalQuantity      开立总量（业务单位）
     * @return 分段列表（各段 quantity 为业务单位）
     * @throws BusinessException 模板/库存不足或参数非法时
     */
    public List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> allocateDeviceFifo(
            Long orgId,
            Long encounterId,
            Long deviceDefinitionId,
            String categoryCode,
            String businessUnitCode,
            BigDecimal totalQuantity
    ) {
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配缺少 orgId");
        }
        if (encounterId == null || deviceDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配缺少 encounterId/deviceDefinitionId");
        }
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "签发 FIFO 分配数量必须大于 0");
        }
        ProductDefinitionDisplayDto productRow = DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
                productDefinitionDisplayTerminologyAssembler,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                deviceDefinitionId);
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
                orgId,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                distributionCategory,
                deviceDefinitionId,
                null,
                productRow.getTypeCode(),
                null);
        if (candidateLocs.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "未解析到候选发放库位，无法按 FIFO 拆分耗材发放");
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.ADM_DEVICE_DEFINITION, List.of(deviceDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildDeviceOccupancy(candidateLocs, deviceDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                physical, occ, TableNameConstants.ADM_DEVICE_DEFINITION, productRow, businessUnitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 拒绝签发时点名耗材，便于护士/医生直接改量或换品
            throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT,
                    String.format(
                            DoctorStationAdviceConstants.MSG_SIGN_FIFO_INSUFFICIENT_STOCK_FOR_DEVICE,
                            resolveFifoProductDisplayName(productRow, "耗材")));
        }
        return toDeviceDispenseSegments(fifoResult.getSegments());
    }

    /**
     * 按指定发放库房（护士划价/绑定执行已解析的 performLocation）做耗材 FIFO 分段。
     *
     * @param encounterId        就诊主键
     * @param performLocationId  发放库房主键（adm_location.id）
     * @param deviceDefinitionId 耗材定义主键
     * @param unitCode           业务单位编码
     * @param totalQuantity      开立总量（业务单位）
     * @return FIFO 分段列表
     * @throws BusinessException 库房为空或库存不足时
     */
    public List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> allocateDeviceFifoAtPerformLocation(
        Long encounterId,
        Long performLocationId,
        Long deviceDefinitionId,
        String unitCode,
        BigDecimal totalQuantity) {
        if (performLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放库房不能为空");
        }
        if (encounterId == null || deviceDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分配缺少 encounterId/deviceDefinitionId");
        }
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分配数量必须大于 0");
        }
        if (unitCode == null || unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分配缺少 unitCode");
        }
        ProductDefinitionDisplayDto productRow = DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
            productDefinitionDisplayTerminologyAssembler,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            deviceDefinitionId);
        List<Long> candidateLocs = List.of(performLocationId);
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.ADM_DEVICE_DEFINITION, List.of(deviceDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildDeviceOccupancy(candidateLocs, deviceDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
            physical, occ, TableNameConstants.ADM_DEVICE_DEFINITION, productRow, unitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT,
                "【" + resolveFifoProductDisplayName(productRow, "耗材") + "】指定库房可用库存不足以覆盖耗材数量");
        }
        return toDeviceDispenseSegments(fifoResult.getSegments());
    }

    /**
     * FIFO 草稿分段 → 耗材发放替换命令分段。
     *
     * <p>每段须带真实库存批号（对齐 **PR-DD-LOT-002** / **BR-ORD-033**）；空批号拒绝进入签发与挂费。</p>
     *
     * @param drafts 分配结果
     * @return 命令分段列表
     * @throws BusinessException 任一段批号为空时
     */
    private static List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> toDeviceDispenseSegments(
            List<DoctorStationAdviceFifoSegmentDraft> drafts
    ) {
        List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> segments = new ArrayList<>();
        // Gate：无草稿则返回空列表
        if (drafts == null) {
            return segments;
        }
        for (DoctorStationAdviceFifoSegmentDraft draft : drafts) {
            // Skip：空草稿不入分段
            if (draft == null) {
                continue;
            }
            // Gate：库存 FIFO 段须带真实批号，禁止空批进入签发/挂费
            if (StringUtils.isEmpty(draft.getLotNumber())) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT,
                    "库存可用行缺少批号，无法按 FIFO 签发");
            }
            ReplaceFifoDeviceDispenseDraftsCommand.Segment segment = new ReplaceFifoDeviceDispenseDraftsCommand.Segment();
            segment.setLocationId(draft.getLocationId());
            segment.setLotNumber(draft.getLotNumber().trim());
            segment.setQuantity(draft.getQuantity());
            segments.add(segment);
        }
        return segments;
    }

    /**
     * 构建药品占用映射（表+定义+库位复合键 → 占用量）；候选库房全局汇总，不限就诊。
     *
     * @param candidateLocationIds   候选库位
     * @param medicationDefinitionId 药品定义主键
     * @param productRow             药品读模型（业务单位→拆零换算）
     * @return 占用量映射
     */
    private Map<String, BigDecimal> buildMedicationOccupancy(
            List<Long> candidateLocationIds,
            Long medicationDefinitionId,
            ProductDefinitionDisplayDto productRow
    ) {
        List<MedicationDispenseOrderCatalogOccupancyModel> occRows = medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                candidateLocationIds, List.of(medicationDefinitionId));
        return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, TableNameConstants.MED_MEDICATION_DEFINITION);
    }

    /**
     * 构建耗材占用映射（拆零单位汇总）；候选库房全局汇总，不限就诊。
     *
     * @param candidateLocationIds 候选库位
     * @param deviceDefinitionId   耗材定义主键
     * @param productRow           耗材产品读模型
     * @return 占用量映射
     */
    private Map<String, BigDecimal> buildDeviceOccupancy(
            List<Long> candidateLocationIds,
            Long deviceDefinitionId,
            ProductDefinitionDisplayDto productRow
    ) {
        List<DeviceDispenseOrderCatalogOccupancyModel> occRows = deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                candidateLocationIds, List.of(deviceDefinitionId));
        return DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occRows, productRow, TableNameConstants.ADM_DEVICE_DEFINITION);
    }

    /**
     * 库存行排序：先按模板库位顺序，再按效期优先（空效期靠后），最后用库存主键稳定次序。
     *
     * @param rows           库存可用行（将被原地排序）
     * @param candidateOrder 模板给出的库位次序
     */
    private static void sortInventoryRowsForTemplateFifo(List<InventoryCatalogAvailabilityRowModel> rows, List<Long> candidateOrder) {
        Map<Long, Integer> locOrder = new HashMap<>();
        int idx = 0;
        for (Long locId : candidateOrder) {
            locOrder.put(locId, idx);
            idx = idx + 1;
        }
        rows.sort((left, right) -> {
            int la = locOrder.getOrDefault(left.getLocationId(), Integer.MAX_VALUE);
            int lb = locOrder.getOrDefault(right.getLocationId(), Integer.MAX_VALUE);
            if (la != lb) {
                if (la < lb) {
                    return -1;
                }
                return 1;
            }
            LocalDateTime ea = left.getExpirationDate();
            LocalDateTime eb = right.getExpirationDate();
            if (ea == null && eb == null) {
                return compareLong(left.getId(), right.getId());
            }
            if (ea == null) {
                return 1;
            }
            if (eb == null) {
                return -1;
            }
            int c = ea.compareTo(eb);
            if (c != 0) {
                return c;
            }
            return compareLong(left.getId(), right.getId());
        });
    }

    /**
     * 解析 FIFO 库存不足提示中的产品展示名（通用名优先，其次商品名）。
     *
     * @param productRow 产品读模型
     * @param fallback   名称均空时的兜底文案（如「药品」「耗材」）
     * @return 非空展示名
     */
    private static String resolveFifoProductDisplayName(ProductDefinitionDisplayDto productRow, String fallback) {
        if (productRow == null) {
            return fallback;
        }
        String name = StringUtils.firstNonBlank(productRow.getProductName(), productRow.getMerchandiseName());
        if (StringUtils.isEmpty(name)) {
            return fallback;
        }
        return name;
    }

    /**
     * 安全比较 Long 型主键（空视为 0）。
     *
     * @param a 左值
     * @param b 右值
     * @return 比较结果
     */
    private static int compareLong(Long a, Long b) {
        long va = 0L;
        if (a != null) {
            va = a.longValue();
        }
        long vb = 0L;
        if (b != null) {
            vb = b.longValue();
        }
        if (va < vb) {
            return -1;
        }
        if (va > vb) {
            return 1;
        }
        return 0;
    }
}
