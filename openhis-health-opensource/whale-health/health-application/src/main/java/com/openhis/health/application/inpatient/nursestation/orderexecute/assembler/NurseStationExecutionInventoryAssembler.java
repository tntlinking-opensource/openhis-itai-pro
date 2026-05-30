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

import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.DispenseExeReserveRowModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionDisplaySupport;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行库存 Gate FIFO dry-run（IF-IP-EXE-02，BR-IP-EXE-014）。
 *
 * <p>参照 {@link com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceFifoAllocationAssembler}，
 * 预占口径为 {@link MedicationDispenseQueryService#listDispenseExeReserve} 全局汇总。
 * 绑定耗材库存 Gate 不在本模块处理，由其他页面统一编排。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationExecutionInventoryAssembler {

    /** 候选药房顺位解析。 */
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;
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
        /** 目录分类编码；空则回落 DEFAULT。 */
        private String categoryCode;
        /** 业务单位数量（长期=单次量，临时=总量）。 */
        private BigDecimal businessQuantity;
        /** 业务单位编码。 */
        private String unitCode;
    }

    /**
     * FIFO dry-run 分配结果（单药品一条，含库位+批号）。
     */
    @Getter
    @Setter
    public static class MedicationFifoAllocation {
        /** 发药库位主键。 */
        private Long locationId;
        /** 批号。 */
        private String lotNumber;
    }

    /**
     * 对批量执行需求做 FIFO dry-run；任一药品不足则整批失败（EX-IP-EXE-302）。
     *
     * @param demandLines 库存 Gate 需求行；仅药嘱汇总后传入
     * @return medicationId → FIFO 分配结果
     * @throws BusinessException EX-IP-EXE-302 库存不足
     */
    public Map<Long, MedicationFifoAllocation> runGateDryRun(List<InventoryGateDemandLine> demandLines) {
        Map<Long, MedicationFifoAllocation> result = new HashMap<>();
        if (demandLines == null || demandLines.isEmpty()) {
            return result;
        }
        // ① 按 medicationId 聚合拆零单位需求量（长期=单次量，临时=总量）
        Map<Long, BigDecimal> aggregatedSplitQtyByMedication = this.aggregateSplitQuantities(demandLines);
        if (aggregatedSplitQtyByMedication.isEmpty()) {
            return result;
        }
        Long orgId = this.resolveOrgId(demandLines);
        Set<Long> medicationIds = aggregatedSplitQtyByMedication.keySet();
        // ② 解析候选药房库位顺位（ServiceLocationTemplate）
        List<Long> candidateLocationIds = this.resolveCandidateLocationIds(orgId, medicationIds, demandLines);
        if (candidateLocationIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_302,
                "未配置候选药房，库存 Gate 失败");
        }
        // ③ 装载物理批次可用量，按模板顺位+效期 FIFO 排序
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.MED_MEDICATION_DEFINITION, new ArrayList<>(medicationIds), candidateLocationIds);
        this.sortInventoryRowsForTemplateFifo(physical, candidateLocationIds);
        // ④ 全局待发预占（不限就诊）扣减库位级可用量
        Map<String, BigDecimal> globalReserve = this.buildGlobalReserveMap(
            candidateLocationIds, medicationIds);
        Map<String, BigDecimal> virtualNetByRowKey = this.buildVirtualNetByRowKey(physical, globalReserve);
        // ⑤ 逐药品 FIFO dry-run；任一不足整批 EX-IP-EXE-302
        for (Map.Entry<Long, BigDecimal> entry : aggregatedSplitQtyByMedication.entrySet()) {
            Long medicationId = entry.getKey();
            BigDecimal requiredSplitQty = entry.getValue();
            MedicationFifoAllocation allocation = this.allocateSingleCatalogFifo(
                medicationId, requiredSplitQty, physical, virtualNetByRowKey);
            if (allocation == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_302,
                    "药品库存不足，无法执行所选计划点");
            }
            result.put(medicationId, allocation);
        }
        return result;
    }

    /**
     * 单目录品 FIFO 分配；成功返回首段库位+批号。
     */
    private MedicationFifoAllocation allocateSingleCatalogFifo(
        Long itemId,
        BigDecimal requiredQty,
        List<InventoryCatalogAvailabilityRowModel> physical,
        Map<String, BigDecimal> virtualNetByRowKey) {
        BigDecimal remaining = requiredQty;
        MedicationFifoAllocation firstSegment = null;
        for (InventoryCatalogAvailabilityRowModel row : physical) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (row == null || !itemId.equals(row.getItemId())) {
                // 只处理当前药品的批次行
                continue;
            }
            String rowKey = this.catalogRowKey(row);
            BigDecimal net = virtualNetByRowKey.getOrDefault(rowKey, BigDecimal.ZERO);
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                // 该批次已被前面药品/预占扣净
                continue;
            }
            BigDecimal take = net.min(remaining);
            if (firstSegment == null) {
                firstSegment = new MedicationFifoAllocation();
                firstSegment.setLocationId(row.getLocationId());
                firstSegment.setLotNumber(row.getLotNumber());
            }
            // dry-run 扣减虚拟库存，同批次后续槽位共享 working copy
            virtualNetByRowKey.put(rowKey, net.subtract(take));
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // 全部批次扣完仍不足 → 该药品 Gate 失败
            return null;
        }
        return firstSegment;
    }

    /**
     * 按药品聚合拆零单位需求量。
     */
    private Map<Long, BigDecimal> aggregateSplitQuantities(List<InventoryGateDemandLine> demandLines) {
        Map<Long, BigDecimal> aggregated = new LinkedHashMap<>();
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = this.batchLoadMedicationProducts(demandLines);
        for (InventoryGateDemandLine line : demandLines) {
            if (line == null || line.getMedicationId() == null) {
                continue;
            }
            if (line.getBusinessQuantity() == null || line.getBusinessQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ProductDefinitionDisplayDto product = productByMedicationId.get(line.getMedicationId());
            // 业务单位 → 拆零单位，与库存批次 quantity 口径对齐
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(product), line.getUnitCode(), line.getBusinessQuantity());
            BigDecimal prev = aggregated.get(line.getMedicationId());
            if (prev == null) {
                prev = BigDecimal.ZERO;
            }
            // 同批次多条槽位可能重复同一 medicationId，需求量累加
            aggregated.put(line.getMedicationId(), prev.add(splitQty));
        }
        return aggregated;
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
            // 无机构无法解析药房模板 → 上层抛 EX-IP-EXE-302
            return new ArrayList<>(merged);
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = this.batchLoadMedicationProducts(demandLines);
        Map<Long, String> categoryByMedication = new HashMap<>();
        for (InventoryGateDemandLine line : demandLines) {
            if (line == null || line.getMedicationId() == null) {
                continue;
            }
            if (!categoryByMedication.containsKey(line.getMedicationId())) {
                ProductDefinitionDisplayDto product = productByMedicationId.get(line.getMedicationId());
                String productCategory = null;
                if (product != null) {
                    productCategory = product.getCategoryCode();
                }
                // 与住院医生站目录/FIFO 一致：优先行内 category，再产品读模型 category_code，最后 DEFAULT
                String category = StringUtils.firstNonBlank(
                    line.getCategoryCode(),
                    productCategory,
                    MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
                categoryByMedication.put(line.getMedicationId(), category);
            }
        }
        for (Long medicationId : medicationIds) {
            String category = categoryByMedication.get(medicationId);
            if (category == null) {
                category = MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
            }
            // 按药品目录分类查 ServiceLocationTemplate 候选药房顺位
            List<Long> locs = serviceLocationTemplateQueryService.listCandidateDistributionLocationIds(
                orgId, TableNameConstants.MED_MEDICATION_DEFINITION, category, null);
            for (Long locId : locs) {
                // LinkedHashSet 保序去重：多药品候选库位合并为统一 FIFO 扫描序
                merged.add(locId);
            }
        }
        return new ArrayList<>(merged);
    }

    /**
     * 构建全局预占映射：location+medication → 预占量（拆零口径与可用量对齐由读侧保证）。
     */
    private Map<String, BigDecimal> buildGlobalReserveMap(List<Long> locationIds, Set<Long> medicationIds) {
        Map<String, BigDecimal> reserve = new HashMap<>();
        List<DispenseExeReserveRowModel> rows = medicationDispenseQueryService.listDispenseExeReserve(
            locationIds, medicationIds);
        for (DispenseExeReserveRowModel row : rows) {
            if (row == null || row.getLocationId() == null || row.getMedicationId() == null) {
                continue;
            }
            String key = this.locationMedicationKey(row.getLocationId(), row.getMedicationId());
            BigDecimal qty = row.getReserveQuantity();
            if (qty == null) {
                qty = BigDecimal.ZERO;
            }
            // 同一库位+药品可能有多条预占行，累加为总量
            BigDecimal prev = reserve.getOrDefault(key, BigDecimal.ZERO);
            reserve.put(key, prev.add(qty));
        }
        return reserve;
    }

    /**
     * 初始化批次行净可用量（物理量 − 全局库位级预占分摊到首行前的 working copy）。
     */
    private Map<String, BigDecimal> buildVirtualNetByRowKey(
        List<InventoryCatalogAvailabilityRowModel> physical,
        Map<String, BigDecimal> globalReserve) {
        Map<String, BigDecimal> virtualNet = new HashMap<>();
        Map<String, BigDecimal> reserveRemain = new HashMap<>(globalReserve);
        for (InventoryCatalogAvailabilityRowModel row : physical) {
            if (row == null || row.getItemId() == null || row.getLocationId() == null) {
                continue;
            }
            String rowKey = this.catalogRowKey(row);
            BigDecimal net = row.getQuantity();
            if (net == null) {
                net = BigDecimal.ZERO;
            }
            // 全局待发预占按 location+medication 维度扣减（不限就诊，同 IF-DOR-11）
            String locMedKey = this.locationMedicationKey(row.getLocationId(), row.getItemId());
            BigDecimal locked = reserveRemain.getOrDefault(locMedKey, BigDecimal.ZERO);
            if (locked.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal deduct = net.min(locked);
                net = net.subtract(deduct);
                reserveRemain.put(locMedKey, locked.subtract(deduct));
            }
            virtualNet.put(rowKey, net);
        }
        return virtualNet;
    }

    /**
     * 单药品 FIFO 分配；成功返回首段库位+批号。
     */
    private MedicationFifoAllocation allocateSingleMedicationFifo(
        Long medicationId,
        BigDecimal requiredSplitQty,
        List<InventoryCatalogAvailabilityRowModel> physical,
        Map<String, BigDecimal> virtualNetByRowKey) {
        return this.allocateSingleCatalogFifo(medicationId, requiredSplitQty, physical, virtualNetByRowKey);
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
            // 第一关键字：模板配置的药房优先级（护士不可手选，按模板顺位 FIFO）
            int la = locOrder.getOrDefault(left.getLocationId(), Integer.MAX_VALUE);
            int lb = locOrder.getOrDefault(right.getLocationId(), Integer.MAX_VALUE);
            if (la != lb) {
                if (la < lb) {
                    return -1;
                }
                return 1;
            }
            // 第二关键字：效期近者优先（近效期先出）
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
            // 第三关键字：稳定 tie-break
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

    private String catalogRowKey(InventoryCatalogAvailabilityRowModel row) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        String lot = StringUtils.defaultString(row.getLotNumber());
        return row.getItemId() + String.valueOf(sep) + row.getLocationId() + String.valueOf(sep) + lot;
    }

    private String locationMedicationKey(Long locationId, Long medicationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return locationId + String.valueOf(sep) + medicationId;
    }
}
