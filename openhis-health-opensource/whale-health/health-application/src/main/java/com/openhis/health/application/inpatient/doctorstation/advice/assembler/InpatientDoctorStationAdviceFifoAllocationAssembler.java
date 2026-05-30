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

package com.openhis.health.application.inpatient.doctorstation.advice.assembler;

import com.openhis.health.application.common.assembler.product.DoctorStationAdviceFifoAllocationResult;
import com.openhis.health.application.common.assembler.product.DoctorStationAdviceFifoSegmentDraft;
import com.openhis.health.application.common.assembler.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
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
 * 住院医生站签发相位（仅临时药品）：按模板库位 + 批次效期做 FIFO 拆分，产出发放草稿分段。
 *
 * <p>库存比对与占用扣减均在<strong>拆零单位</strong>；发放分段 {@code quantity} 与医嘱开立单位一致，整包装开立禁止小数盒切分
 * （与门诊 {@link DoctorStationAdviceFifoAllocationAssembler} 同口径）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceFifoAllocationAssembler {

    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /**
     * 药品：按 FIFO 产出签发后草稿发放分段。
     *
     * @param orgId                  开立/接诊机构，用于模板解析
     * @param encounterId            就诊主键（占用扣减边界）
     * @param medicationDefinitionId 药品定义主键
     * @param categoryCode           目录分类编码；空则回落 DEFAULT
     * @param businessUnitCode       医嘱开立数量单位编码
     * @param totalQuantity          医嘱开立总量（业务单位）
     * @return 有序分段列表
     * @throws BusinessException 模板无候选库位、库存不足以覆盖总量或参数非法时
     */
    public List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> allocateMedicationFifo(
            Long orgId,
            Long encounterId,
            Long medicationDefinitionId,
            String categoryCode,
            String businessUnitCode,
            BigDecimal totalQuantity
    ) {
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_SIGN_FIFO_MISSING_ORG_ID);
        }
        if (encounterId == null || medicationDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_SIGN_FIFO_MISSING_ENCOUNTER_OR_MED_DEF);
        }
        if (totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_SIGN_FIFO_QUANTITY_MUST_BE_POSITIVE);
        }
        ProductDefinitionDisplayDto productRow = DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
                productDefinitionBatchLoader,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationDefinitionId);
        // Pre：按机构+药品定义解析候选发放库位（模板顺序即 FIFO 第一层排序键）。
        String distributionCategory = StringUtils.firstNonBlank(categoryCode, MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> candidateLocs = serviceLocationTemplateQueryService.listCandidateDistributionLocationIds(
                orgId, TableNameConstants.MED_MEDICATION_DEFINITION, distributionCategory, null);
        if (candidateLocs.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
                    DoctorStationAdviceConstants.MSG_SIGN_FIFO_NO_CANDIDATE_LOCATION);
        }
        // Pre：在候选库位上装载批次级可用库存，并按模板库位序+效期排序。
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.MED_MEDICATION_DEFINITION, List.of(medicationDefinitionId), candidateLocs);
        sortInventoryRowsForTemplateFifo(physical, candidateLocs);
        // Pre：扣减同就诊下已占用量（含已签发草稿发放），得到每条库存行的净可用量（拆零单位）。
        Map<String, BigDecimal> occ = buildMedicationOccupancy(encounterId, candidateLocs, medicationDefinitionId, productRow);
        DoctorStationAdviceFifoAllocationResult fifoResult = DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                physical, occ, TableNameConstants.MED_MEDICATION_DEFINITION, productRow, businessUnitCode, totalQuantity);
        // 规模门控：全候选库位仍不足以覆盖医嘱总量则拒绝签发（BR-IP-ORD-005）。
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
                    DoctorStationAdviceConstants.MSG_SIGN_FIFO_INSUFFICIENT_STOCK_FOR_ADVICE);
        }
        return toMedicationDispenseSegments(fifoResult.getSegments());
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
     * 构建药品占用映射（拆零单位汇总）。
     *
     * @param encounterId            就诊主键
     * @param candidateLocationIds   候选库位
     * @param medicationDefinitionId 药品定义主键
     * @param productRow             药品产品读模型
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
