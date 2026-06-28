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

package com.openhis.health.module.common.assembler.product;

import com.openhis.health.module.common.assembler.product.ClinicalCatalogDispenseLocationResolveSupport;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceFifoAllocationResult;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
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
import org.apache.commons.lang3.StringUtils;
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
 * 跨批次切分以医嘱开立单位为最小粒度：拆零开立可部分切分，盒等整包装开立每段须为整数个开立单位。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationAdviceFifoAllocationAssembler {

    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /**
     * 药品：按 FIFO 产出签发后草稿发放分段。
     *
     * @param orgId                  开立/接诊机构，用于模板解析
     * @param encounterId            就诊主键（占用扣减边界）
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
                productDefinitionBatchLoader,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationDefinitionId);
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
                orgId,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                distributionCategory,
                medicationDefinitionId,
                methodCode,
                null);
        if (candidateLocs.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "未解析到候选发放库位，无法按 FIFO 拆分发药");
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.MED_MEDICATION_DEFINITION, List.of(medicationDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildMedicationOccupancy(encounterId, candidateLocs, medicationDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                physical, occ, TableNameConstants.MED_MEDICATION_DEFINITION, productRow, businessUnitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "候选库位可用库存不足以覆盖医嘱数量，拒绝签发");
        }
        return toMedicationDispenseSegments(fifoResult.getSegments());
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
        try {
            List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments = allocateMedicationFifo(
                orgId, encounterId, medicationDefinitionId, categoryCode, methodCode, businessUnitCode, totalQuantity);
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
     * @param drafts 分配结果
     * @return 命令分段列表
     */
    private static List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> toMedicationDispenseSegments(
            List<DoctorStationAdviceFifoSegmentDraft> drafts
    ) {
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments = new ArrayList<>();
        if (drafts == null) {
            return segments;
        }
        for (DoctorStationAdviceFifoSegmentDraft draft : drafts) {
            if (draft == null) {
                continue;
            }
            ReplaceFifoMedicationDispenseDraftsCommand.Segment segment = new ReplaceFifoMedicationDispenseDraftsCommand.Segment();
            segment.setLocationId(draft.getLocationId());
            segment.setLotNumber(draft.getLotNumber());
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
                productDefinitionBatchLoader,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                deviceDefinitionId);
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
                orgId,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                distributionCategory,
                deviceDefinitionId,
                null,
                null);
        if (candidateLocs.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "未解析到候选发放库位，无法按 FIFO 拆分耗材发放");
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.ADM_DEVICE_DEFINITION, List.of(deviceDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildDeviceOccupancy(encounterId, candidateLocs, deviceDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                physical, occ, TableNameConstants.ADM_DEVICE_DEFINITION, productRow, businessUnitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "候选库位可用库存不足以覆盖耗材医嘱数量，拒绝签发");
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
            productDefinitionBatchLoader,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            deviceDefinitionId);
        List<Long> candidateLocs = List.of(performLocationId);
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.ADM_DEVICE_DEFINITION, List.of(deviceDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        Map<String, BigDecimal> occ = buildDeviceOccupancy(encounterId, candidateLocs, deviceDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
            physical, occ, TableNameConstants.ADM_DEVICE_DEFINITION, productRow, unitCode, totalQuantity);
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "指定库房可用库存不足以覆盖耗材数量");
        }
        return toDeviceDispenseSegments(fifoResult.getSegments());
    }

    /**
     * FIFO 草稿分段 → 耗材发放替换命令分段。
     *
     * @param drafts 分配结果
     * @return 命令分段列表
     */
    private static List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> toDeviceDispenseSegments(
            List<DoctorStationAdviceFifoSegmentDraft> drafts
    ) {
        List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> segments = new ArrayList<>();
        if (drafts == null) {
            return segments;
        }
        for (DoctorStationAdviceFifoSegmentDraft draft : drafts) {
            if (draft == null) {
                continue;
            }
            ReplaceFifoDeviceDispenseDraftsCommand.Segment segment = new ReplaceFifoDeviceDispenseDraftsCommand.Segment();
            segment.setLocationId(draft.getLocationId());
            segment.setLotNumber(draft.getLotNumber());
            segment.setQuantity(draft.getQuantity());
            segments.add(segment);
        }
        return segments;
    }

    /**
     * 构建药品占用映射（表+定义+库位复合键 → 占用量）。
     *
     * @param encounterId            就诊主键
     * @param candidateLocationIds   候选库位
     * @param medicationDefinitionId 药品定义主键
     * @return 占用量映射
     */
    private Map<String, BigDecimal> buildMedicationOccupancy(
            Long encounterId,
            List<Long> candidateLocationIds,
            Long medicationDefinitionId,
            ProductDefinitionDisplayDto productRow
    ) {
        List<MedicationDispenseOrderCatalogOccupancyModel> occRows = medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                encounterId, candidateLocationIds, List.of(medicationDefinitionId));
        return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, TableNameConstants.MED_MEDICATION_DEFINITION);
    }

    /**
     * 构建耗材占用映射（拆零单位汇总）。
     *
     * @param encounterId          就诊主键
     * @param candidateLocationIds 候选库位
     * @param deviceDefinitionId   耗材定义主键
     * @param productRow           耗材产品读模型
     * @return 占用量映射
     */
    private Map<String, BigDecimal> buildDeviceOccupancy(
            Long encounterId,
            List<Long> candidateLocationIds,
            Long deviceDefinitionId,
            ProductDefinitionDisplayDto productRow
    ) {
        List<DeviceDispenseOrderCatalogOccupancyModel> occRows = deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                encounterId, candidateLocationIds, List.of(deviceDefinitionId));
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
