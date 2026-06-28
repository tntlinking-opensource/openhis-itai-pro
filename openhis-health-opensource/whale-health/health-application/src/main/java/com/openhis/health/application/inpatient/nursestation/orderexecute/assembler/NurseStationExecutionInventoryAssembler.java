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

package com.openhis.health.application.inpatient.nursestation.orderexecute.assembler;

import com.openhis.health.module.common.assembler.product.ClinicalCatalogDispenseLocationResolveSupport;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceFifoAllocationResult;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceFifoSegmentDraft;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.domain.medication.medicationdispense.queries.model.DispenseExeReserveRowModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行库存 Gate 与 FIFO 分段（IF-IP-EXE-02，BR-IP-EXE-014/016）。
 *
 * <p>长临药嘱执行均在写路径按模板库位 + 批次效期做 FIFO 分段（与门诊 BR-ORD-021 同口径）；
 * 预占口径为 {@link MedicationDispenseQueryService#listDispenseExeReserve} 全局汇总。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationExecutionInventoryAssembler {

    /** 候选药房顺位解析（categoryCode + calcScore）。 */
    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;
    /** 批次级可用库存装载。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 全局待发预占读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 拆零单位换算读模型。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /**
     * 单条库存 Gate 需求行（仅药嘱；绑定耗材库存由其他页面统一处理）。
     */
    @Getter
    @Setter
    public static class InventoryGateDemandLine {
        /** 开立/接诊机构，用于模板解析。 */
        private Long orgId;
        /** 药品定义主键。 */
        private Long medicationId;
        /** 目录 categoryCode；空则回落产品读模型或 DEFAULT。 */
        private String categoryCode;
        /** 给药途径编码（模板 calcScore 维度）。 */
        private String methodCode;
        /** 业务单位数量（长期=单次量，临时=总量）。 */
        private BigDecimal businessQuantity;
        /** 业务单位编码。 */
        private String unitCode;
    }

    /**
     * 执行写路径 FIFO 分段：库位、批号与本段业务单位数量。
     */
    @Getter
    @Setter
    public static class MedicationFifoSegment {
        /** 发药库位主键。 */
        private Long locationId;
        /** 批号。 */
        private String lotNumber;
        /** 本段发放数量（与医嘱 {@code unit_code} 一致的业务单位）。 */
        private BigDecimal quantity;
    }

    /**
     * 本批药嘱执行 FIFO 分段计划（与 {@link InventoryGateDemandLine} 列表下标一一对应）。
     */
    @Getter
    public static class MedicationFifoBatchPlan {
        /** 每条 Gate 需求行对应的分段列表（可为多段）。 */
        private final List<List<MedicationFifoSegment>> segmentsByDemandLineIndex;

        /**
         * @param segmentsByDemandLineIndex 分段计划
         */
        public MedicationFifoBatchPlan(List<List<MedicationFifoSegment>> segmentsByDemandLineIndex) {
            if (segmentsByDemandLineIndex == null) {
                this.segmentsByDemandLineIndex = Collections.emptyList();
            } else {
                this.segmentsByDemandLineIndex = segmentsByDemandLineIndex;
            }
        }

        /**
         * 按 Gate 需求行下标取 FIFO 分段。
         *
         * @param demandLineIndex Gate 需求行下标
         * @return 分段列表；越界时空列表
         */
        public List<MedicationFifoSegment> getSegments(int demandLineIndex) {
            if (demandLineIndex < 0 || demandLineIndex >= segmentsByDemandLineIndex.size()) {
                return Collections.emptyList();
            }
            List<MedicationFifoSegment> segments = segmentsByDemandLineIndex.get(demandLineIndex);
            if (segments == null) {
                return Collections.emptyList();
            }
            return segments;
        }
    }

    /**
     * 对批量药嘱执行需求做 FIFO 分段 dry-run；按需求行顺序扣减 working 库存，任一不足整批失败。
     *
     * @param demandLines 库存 Gate 需求行（与待执行药嘱槽位顺序一致）
     * @return 分段计划
     * @throws BusinessException EX-IP-EXE-302 库存不足或未配置药房
     */
    public MedicationFifoBatchPlan prepareMedicationFifoBatchPlan(List<InventoryGateDemandLine> demandLines) {
        if (demandLines == null || demandLines.isEmpty()) {
            return new MedicationFifoBatchPlan(Collections.emptyList());
        }
        Long orgId = this.resolveOrgId(demandLines);
        Set<Long> medicationIds = new LinkedHashSet<>();
        for (InventoryGateDemandLine line : demandLines) {
            if (line != null && line.getMedicationId() != null) {
                medicationIds.add(line.getMedicationId());
            }
        }
        if (medicationIds.isEmpty()) {
            return new MedicationFifoBatchPlan(Collections.emptyList());
        }
        // Pre：候选库位、物理批次、全局待发预占（拆零单位，表+定义+库位复合键）
        List<Long> candidateLocationIds = this.resolveCandidateLocationIds(orgId, medicationIds, demandLines);
        if (candidateLocationIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_302,
                "未配置候选药房，库存 Gate 失败");
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.MED_MEDICATION_DEFINITION, new ArrayList<>(medicationIds), candidateLocationIds);
        this.sortInventoryRowsForTemplateFifo(physical, candidateLocationIds);
        inventoryItemQueryService.lockInventoryRowsForCatalogAvailability(physical);
        List<InventoryCatalogAvailabilityRowModel> workingPhysical = this.clonePhysicalRows(physical);
        Map<String, BigDecimal> occupancyByItemLocationKey =
            this.buildGlobalOccupancyByItemLocationKey(candidateLocationIds, medicationIds);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = this.batchLoadMedicationProducts(demandLines);
        List<List<MedicationFifoSegment>> plan = new ArrayList<>();
        // Cmd：逐需求行 FIFO 分段并在 working 库存上扣减，供同批后续槽位继续分配
        for (InventoryGateDemandLine line : demandLines) {
            if (line == null || line.getMedicationId() == null) {
                plan.add(Collections.emptyList());
                continue;
            }
            ProductDefinitionDisplayDto product = productByMedicationId.get(line.getMedicationId());
            List<MedicationFifoSegment> segments = this.allocateSegmentsForDemandLine(
                line, workingPhysical, occupancyByItemLocationKey, product);
            if (segments.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_302,
                    "药品库存不足，无法执行所选计划点");
            }
            this.applySegmentDepletion(workingPhysical, product, line.getUnitCode(), segments);
            plan.add(segments);
        }
        return new MedicationFifoBatchPlan(plan);
    }

    /**
     * 单条需求行 FIFO 分段（BR-ORD-021：整包装整数段、拆零可跨批部分切分）。
     */
    private List<MedicationFifoSegment> allocateSegmentsForDemandLine(
        InventoryGateDemandLine line,
        List<InventoryCatalogAvailabilityRowModel> workingPhysical,
        Map<String, BigDecimal> occupancyByItemLocationKey,
        ProductDefinitionDisplayDto product) {
        if (line.getBusinessQuantity() == null || line.getBusinessQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }
        List<InventoryCatalogAvailabilityRowModel> medRows = new ArrayList<>();
        for (InventoryCatalogAvailabilityRowModel row : workingPhysical) {
            if (row == null || !line.getMedicationId().equals(row.getItemId())) {
                continue;
            }
            medRows.add(row);
        }
        DoctorStationAdviceFifoAllocationResult fifoResult =
            DoctorStationAdviceInventoryUnitQuantityHelper.allocateFifoSegmentsInDoctorBusinessUnit(
                medRows,
                occupancyByItemLocationKey,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                product,
                line.getUnitCode(),
                line.getBusinessQuantity());
        if (fifoResult.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return Collections.emptyList();
        }
        return this.toMedicationFifoSegments(fifoResult.getSegments());
    }

    /**
     * 将 FIFO 分段结果映射为执行写路径分段 DTO。
     */
    private List<MedicationFifoSegment> toMedicationFifoSegments(List<DoctorStationAdviceFifoSegmentDraft> drafts) {
        List<MedicationFifoSegment> segments = new ArrayList<>();
        if (drafts == null) {
            return segments;
        }
        for (DoctorStationAdviceFifoSegmentDraft draft : drafts) {
            if (draft == null) {
                continue;
            }
            MedicationFifoSegment segment = new MedicationFifoSegment();
            segment.setLocationId(draft.getLocationId());
            segment.setLotNumber(draft.getLotNumber());
            segment.setQuantity(draft.getQuantity());
            segments.add(segment);
        }
        return segments;
    }

    /**
     * 在 working 物理库存上扣减已分配分段（同批后续槽位共享）。
     */
    private void applySegmentDepletion(
        List<InventoryCatalogAvailabilityRowModel> workingPhysical,
        ProductDefinitionDisplayDto product,
        String unitCode,
        List<MedicationFifoSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (MedicationFifoSegment segment : segments) {
            if (segment == null || segment.getQuantity() == null) {
                continue;
            }
            BigDecimal takeSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(product), unitCode, segment.getQuantity());
            for (InventoryCatalogAvailabilityRowModel row : workingPhysical) {
                if (row == null) {
                    continue;
                }
                if (!this.matchesInventoryRow(row, segment)) {
                    continue;
                }
                BigDecimal net = row.getQuantity();
                if (net == null) {
                    net = BigDecimal.ZERO;
                }
                row.setQuantity(net.subtract(takeSplit));
                break;
            }
        }
    }

    /**
     * 判断库存行与 FIFO 分段是否同一批次。
     */
    private boolean matchesInventoryRow(InventoryCatalogAvailabilityRowModel row, MedicationFifoSegment segment) {
        if (segment.getLocationId() == null) {
            return false;
        }
        if (!segment.getLocationId().equals(row.getLocationId())) {
            return false;
        }
        return StringUtils.equals(StringUtils.defaultString(row.getLotNumber()), StringUtils.defaultString(segment.getLotNumber()));
    }

    /**
     * 克隆物理库存行供本批 FIFO working copy 使用。
     */
    private List<InventoryCatalogAvailabilityRowModel> clonePhysicalRows(
        List<InventoryCatalogAvailabilityRowModel> source) {
        List<InventoryCatalogAvailabilityRowModel> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (InventoryCatalogAvailabilityRowModel row : source) {
            if (row == null) {
                continue;
            }
            InventoryCatalogAvailabilityRowModel cloned = new InventoryCatalogAvailabilityRowModel();
            cloned.setId(row.getId());
            cloned.setItemTable(row.getItemTable());
            cloned.setItemId(row.getItemId());
            cloned.setLocationId(row.getLocationId());
            cloned.setLotNumber(row.getLotNumber());
            cloned.setExpirationDate(row.getExpirationDate());
            cloned.setQuantity(row.getQuantity());
            copy.add(cloned);
        }
        return copy;
    }

    /**
     * 全局待发预占：表+定义+库位 → 拆零占用量（与 {@link DoctorStationAdviceInventoryUnitQuantityHelper} 键一致）。
     */
    private Map<String, BigDecimal> buildGlobalOccupancyByItemLocationKey(
        List<Long> locationIds,
        Set<Long> medicationIds) {
        Map<String, BigDecimal> occ = new HashMap<>();
        List<DispenseExeReserveRowModel> rows = medicationDispenseQueryService.listDispenseExeReserve(
            locationIds, medicationIds);
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        String table = TableNameConstants.MED_MEDICATION_DEFINITION;
        for (DispenseExeReserveRowModel row : rows) {
            if (row == null || row.getLocationId() == null || row.getMedicationId() == null) {
                continue;
            }
            String key = table + sep + row.getMedicationId() + sep + row.getLocationId();
            BigDecimal qty = row.getReserveQuantity();
            if (qty == null) {
                qty = BigDecimal.ZERO;
            }
            BigDecimal prev = occ.getOrDefault(key, BigDecimal.ZERO);
            occ.put(key, prev.add(qty));
        }
        return occ;
    }

    /**
     * 批量加载药品产品读模型。
     */
    private Map<Long, ProductDefinitionDisplayDto> batchLoadMedicationProducts(List<InventoryGateDemandLine> demandLines) {
        Set<Long> medIds = new LinkedHashSet<>();
        for (InventoryGateDemandLine line : demandLines) {
            if (line != null && line.getMedicationId() != null) {
                medIds.add(line.getMedicationId());
            }
        }
        Map<Long, ProductDefinitionDisplayDto> byMedicationId = new HashMap<>();
        if (medIds.isEmpty()) {
            return byMedicationId;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medId : medIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> byKey = productDefinitionBatchLoader.loadAsMap(lookups);
        for (Long medId : medIds) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, medId);
            ProductDefinitionDisplayDto product = byKey.get(key);
            if (product != null) {
                byMedicationId.put(medId, product);
            }
        }
        return byMedicationId;
    }

    /**
     * 解析机构主键（同一批次应一致，取首条非空）。
     */
    private Long resolveOrgId(List<InventoryGateDemandLine> demandLines) {
        for (InventoryGateDemandLine line : demandLines) {
            if (line != null && line.getOrgId() != null) {
                return line.getOrgId();
            }
        }
        return null;
    }

    /**
     * 解析候选药房库位（同一 org 下合并去重，保持模板顺序）。
     */
    private List<Long> resolveCandidateLocationIds(
        Long orgId,
        Set<Long> medicationIds,
        List<InventoryGateDemandLine> demandLines) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>();
        if (orgId == null) {
            return new ArrayList<>(merged);
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = this.batchLoadMedicationProducts(demandLines);
        for (InventoryGateDemandLine line : demandLines) {
            if (line == null || line.getMedicationId() == null) {
                continue;
            }
            ProductDefinitionDisplayDto product = productByMedicationId.get(line.getMedicationId());
            String category = StringUtils.firstNonBlank(
                line.getCategoryCode(),
                product != null ? product.getCategoryCode() : null,
                MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
            List<Long> locs = clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
                orgId,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                category,
                line.getMedicationId(),
                line.getMethodCode(),
                null);
            for (Long locId : locs) {
                merged.add(locId);
            }
        }
        return new ArrayList<>(merged);
    }

    /**
     * 库存行排序：模板库位序 → 效期 → 主键。
     */
    private static void sortInventoryRowsForTemplateFifo(
        List<InventoryCatalogAvailabilityRowModel> rows,
        List<Long> candidateOrder) {
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
     * 安全比较 Long 型主键。
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
