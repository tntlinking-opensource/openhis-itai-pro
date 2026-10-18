/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.zxplat.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生站医嘱开立与签发：业务单位数量 ↔ 库存拆零单位数量的共享换算（与药房发药扣减 {@link MedicationProductQuantityToSplitUnitConverter} 同口径）。
 *
 * <p>库房/供应链路已按拆零持久化的场景禁止再调用本类（见 {@code SupplyPersistedSplitQuantity}）。</p>
 */
public final class DoctorStationAdviceInventoryUnitQuantityHelper {

    /**
     * 工具类禁止实例化。
     */
    private DoctorStationAdviceInventoryUnitQuantityHelper() {
    }

    /**
     * 按产品表与定义主键加载单条 {@link ProductDefinitionDisplayDto}。
     *
     * @param productDefinitionDisplayTerminologyAssembler 产品定义批量加载器
     * @param productTableName             产品来源表名
     * @param productDefinitionId          定义主键
     * @return 展示 DTO
     * @throws BusinessException 未命中
     */
    public static ProductDefinitionDisplayDto requireProductDefinition(
        ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler,
        String productTableName,
        Long productDefinitionId
    ) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(productTableName);
        lookup.setProductDefinitionId(productDefinitionId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(productTableName, productDefinitionId);
        ProductDefinitionDisplayDto productRow = map.get(key);
        if (productRow == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "未找到产品定义，无法换算库存最小单位数量");
        }
        return productRow;
    }

    /**
     * 医嘱/发放业务单位数量 → 库存拆零单位数量。
     *
     * @param productRow       产品展示 DTO
     * @param businessUnitCode 业务单位编码（与医嘱 {@code unit_code} 一致）
     * @param businessQuantity 业务单位下数量
     * @return 拆零单位数量
     */
    public static BigDecimal businessQuantityToSplitUnit(
        ProductDefinitionDisplayDto productRow,
        String businessUnitCode,
        BigDecimal businessQuantity
    ) {
        return MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(productRow),
            businessUnitCode,
            businessQuantity);
    }

    /**
     * 库存拆零单位数量 → 指定业务单位数量（目录展示等非 FIFO 分段场景使用）。
     *
     * <p>签发 FIFO 分段须调用 {@link #allocateFifoSegmentsInDoctorBusinessUnit}，禁止用本方法回写发放段，
     * 以免非拆零开立单位出现 0.6 盒等小数切分。</p>
     *
     * @param productRow       产品展示 DTO
     * @param businessUnitCode 目标业务单位编码
     * @param splitQuantity    拆零单位数量
     * @return 业务单位数量
     */
    public static BigDecimal splitQuantityToBusinessUnit(
        ProductDefinitionDisplayDto productRow,
        String businessUnitCode,
        BigDecimal splitQuantity
    ) {
        if (splitQuantity == null) {
            return null;
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(productRow, null, null);
        return ProductDefinitionCatalogUnitBandsAssembler.convertSplitQuantityToDisplayUnit(
            bands, splitQuantity, businessUnitCode);
    }

    /**
     * 签发 FIFO：在已排序批次库存上按医嘱开立单位切分发放段。
     *
     * <p>开立单位为拆零单位（相对拆零倍数=1）时，允许跨批次按拆零量部分切分（如 10 片 → 6 片 + 4 片）；
     * 开立单位为盒/采购/剂量等非拆零单位时，每段 {@code quantity} 必须为整「开立单位」个数，
     * 单批次净可用不足以凑满 1 个开立单位则跳过该批次、继续 FIFO 下一批（如 3 盒 → A 批 2 盒 + C 批 1 盒）。</p>
     *
     * @param inventoryRows              已按模板库位序+效期排序的批次可用行
     * @param occupancyByItemLocationKey 表+定义+库位 → 拆零预占量
     * @param itemTable                  物品来源表名（复合键前缀）
     * @param productRow                 产品展示 DTO
     * @param businessUnitCode           医嘱开立单位（与 {@code MedicationRequest#unitCode} 一致，不可改）
     * @param totalBusinessQuantity      医嘱开立总量（业务单位）
     * @return 分段列表及仍未覆盖的拆零待分配量
     * @throws BusinessException 开立单位无法解析相对拆零倍数时
     */
    public static DoctorStationAdviceFifoAllocationResult allocateFifoSegmentsInDoctorBusinessUnit(
        List<InventoryCatalogAvailabilityRowModel> inventoryRows,
        Map<String, BigDecimal> occupancyByItemLocationKey,
        String itemTable,
        ProductDefinitionDisplayDto productRow,
        String businessUnitCode,
        BigDecimal totalBusinessQuantity
    ) {
        List<DoctorStationAdviceFifoSegmentDraft> segments = new ArrayList<>();
        BigDecimal remainingBusiness = totalBusinessQuantity;
        BigDecimal remainingSplit = businessQuantityToSplitUnit(productRow, businessUnitCode, totalBusinessQuantity);
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(productRow, null, null);
        BigDecimal ratioToSplit = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(
            bands, businessUnitCode);
        if (ratioToSplit == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "医嘱开立单位无法解析相对拆零倍数，无法按先进先出分配发放批次");
        }
        // 仅拆零单位本身（倍数=1）允许跨批次按片切分；盒等整包装开立必须整单位落段。
        boolean allowPartialAcrossLots = ratioToSplit.compareTo(BigDecimal.ONE) == 0;
        if (inventoryRows == null || inventoryRows.isEmpty()) {
            return new DoctorStationAdviceFifoAllocationResult(segments, remainingSplit);
        }
        // 库位级预占按 FIFO 批次顺序消耗一次，禁止对每一批都减整库占用。
        Map<String, BigDecimal> remainingOccupancy = copyOccupancyRemaining(occupancyByItemLocationKey);
        for (InventoryCatalogAvailabilityRowModel row : inventoryRows) {
            if (remainingSplit.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (row == null) {
                continue;
            }
            // 跳过无真实批号库存行，禁止签发写入空批号发放单
            String rowLot = row.getLotNumber();
            if (rowLot == null || rowLot.trim().isEmpty()) {
                continue;
            }
            BigDecimal netSplit = consumeLocationOccupancyForLot(
                remainingOccupancy, itemTable, row.getItemId(), row.getLocationId(), row.getQuantity());
            // 该库位/批次预减后无可用拆零量，尝试下一批。
            if (netSplit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal takeBusiness;
            BigDecimal takeSplit;
            if (allowPartialAcrossLots) {
                // 医生以片（拆零）开立：允许本批只覆盖部分总量。
                takeSplit = netSplit.min(remainingSplit);
                if (takeSplit.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                takeBusiness = takeSplit;
            } else {
                // 医生以盒等整包装开立：本批须至少能凑满 1 个开立单位，否则跳过找下一批。
                BigDecimal maxWholeBusiness = netSplit.divide(ratioToSplit, 0, RoundingMode.DOWN);
                if (maxWholeBusiness.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                takeBusiness = maxWholeBusiness.min(remainingBusiness);
                if (takeBusiness.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                takeSplit = takeBusiness.multiply(ratioToSplit);
            }
            DoctorStationAdviceFifoSegmentDraft draft = new DoctorStationAdviceFifoSegmentDraft();
            draft.setLocationId(row.getLocationId());
            draft.setLotNumber(row.getLotNumber());
            draft.setQuantity(takeBusiness);
            segments.add(draft);
            remainingBusiness = remainingBusiness.subtract(takeBusiness);
            remainingSplit = remainingSplit.subtract(takeSplit);
        }
        return new DoctorStationAdviceFifoAllocationResult(segments, remainingSplit);
    }

    /**
     * 按 FIFO 序找第一个「单批净可用 ≥ 开立总量」的批次，整量落该批（门诊皮试 BR-ORD-034）。
     *
     * <p>更近效期但不够量的批号直接跳过；各候选独立套用库位预占（视为整量只取该批）。
     * 找不到够量单批时返回 {@code null}，由调用方决定提示跨批或走普通 FIFO。</p>
     *
     * @param inventoryRows              已按模板库位序+效期排序的批次可用行
     * @param occupancyByItemLocationKey 表+定义+库位 → 拆零预占量
     * @param itemTable                  物品来源表名（复合键前缀）
     * @param productRow                 产品展示 DTO
     * @param businessUnitCode           医嘱开立单位
     * @param totalBusinessQuantity      医嘱开立总量（业务单位）
     * @return 单批分段草稿；无一单批够量时为 {@code null}
     * @throws BusinessException 开立单位无法解析相对拆零倍数时
     */
    public static DoctorStationAdviceFifoSegmentDraft tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit(
        List<InventoryCatalogAvailabilityRowModel> inventoryRows,
        Map<String, BigDecimal> occupancyByItemLocationKey,
        String itemTable,
        ProductDefinitionDisplayDto productRow,
        String businessUnitCode,
        BigDecimal totalBusinessQuantity
    ) {
        // Gate：总量非法则无法单批覆盖
        if (totalBusinessQuantity == null || totalBusinessQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (inventoryRows == null || inventoryRows.isEmpty()) {
            return null;
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(productRow, null, null);
        BigDecimal ratioToSplit = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(
            bands, businessUnitCode);
        if (ratioToSplit == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "医嘱开立单位无法解析相对拆零倍数，无法按先进先出分配发放批次");
        }
        boolean allowPartialAcrossLots = ratioToSplit.compareTo(BigDecimal.ONE) == 0;
        for (InventoryCatalogAvailabilityRowModel row : inventoryRows) {
            // Skip：空行或无批号库存不可作为皮试单批落点
            if (row == null) {
                continue;
            }
            String rowLot = row.getLotNumber();
            if (rowLot == null || rowLot.trim().isEmpty()) {
                continue;
            }
            // Pre：每个候选批独立拷贝预占，模拟「整量只取该批」时的库位占用
            Map<String, BigDecimal> occupancyForCandidate = copyOccupancyRemaining(occupancyByItemLocationKey);
            BigDecimal netSplit = consumeLocationOccupancyForLot(
                occupancyForCandidate, itemTable, row.getItemId(), row.getLocationId(), row.getQuantity());
            // 该批预减后无可用量，继续 FIFO 下一批
            if (netSplit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal coverableBusiness;
            if (allowPartialAcrossLots) {
                // 拆零开立：净拆零量即可覆盖的业务量
                coverableBusiness = netSplit;
            } else {
                // 整包装开立：仅整数个开立单位计入可覆盖量
                coverableBusiness = netSplit.divide(ratioToSplit, 0, RoundingMode.DOWN);
            }
            // 本批单批够量：整量落此批，不再跨批
            if (coverableBusiness.compareTo(totalBusinessQuantity) >= 0) {
                DoctorStationAdviceFifoSegmentDraft draft = new DoctorStationAdviceFifoSegmentDraft();
                draft.setLocationId(row.getLocationId());
                draft.setLotNumber(rowLot.trim());
                draft.setQuantity(totalBusinessQuantity);
                return draft;
            }
        }
        // 无一单批够量
        return null;
    }

    /**
     * 同就诊药品发放预占：按发放行 {@code unit_code} 换算为拆零单位后，按「表+定义+库位」复合键汇总。
     *
     * @param occupancyRows 占用行（须含 {@code unitCode}）
     * @param productRow    药品产品展示 DTO
     * @param itemTable     物品来源表名（复合键前缀）
     * @return 拆零单位占用量映射
     */
    public static Map<String, BigDecimal> mergeMedicationOccupancyInSplitUnits(
        List<MedicationDispenseOrderCatalogOccupancyModel> occupancyRows,
        ProductDefinitionDisplayDto productRow,
        String itemTable
    ) {
        Map<String, BigDecimal> acc = new HashMap<>();
        if (occupancyRows == null || occupancyRows.isEmpty()) {
            return acc;
        }
        for (MedicationDispenseOrderCatalogOccupancyModel row : occupancyRows) {
            if (row == null) {
                continue;
            }
            BigDecimal businessQty = row.getQuantity();
            if (businessQty == null) {
                businessQty = BigDecimal.ZERO;
            }
            if (businessQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal splitQty = businessQty;
            if (StringUtils.isNotEmpty(row.getUnitCode()) && productRow != null) {
                splitQty = businessQuantityToSplitUnit(productRow, row.getUnitCode(), businessQty);
            }
            String key = ProductTableNameHelper.tripleCompositeKey(itemTable, row.getMedicationId(), row.getLocationId());
            BigDecimal prev = acc.getOrDefault(key, BigDecimal.ZERO);
            acc.put(key, prev.add(splitQty));
        }
        return acc;
    }

    /**
     * 同就诊耗材发放预占：换算为拆零单位后汇总。
     *
     * @param occupancyRows 占用行
     * @param productRow    耗材产品展示 DTO
     * @param itemTable     物品来源表名
     * @return 拆零单位占用量映射
     */
    public static Map<String, BigDecimal> mergeDeviceOccupancyInSplitUnits(
        List<DeviceDispenseOrderCatalogOccupancyModel> occupancyRows,
        ProductDefinitionDisplayDto productRow,
        String itemTable
    ) {
        Map<String, BigDecimal> acc = new HashMap<>();
        if (occupancyRows == null || occupancyRows.isEmpty()) {
            return acc;
        }
        for (DeviceDispenseOrderCatalogOccupancyModel row : occupancyRows) {
            if (row == null) {
                continue;
            }
            BigDecimal businessQty = row.getQuantity();
            if (businessQty == null) {
                businessQty = BigDecimal.ZERO;
            }
            if (businessQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal splitQty = businessQty;
            if (StringUtils.isNotEmpty(row.getUnitCode()) && productRow != null) {
                splitQty = businessQuantityToSplitUnit(productRow, row.getUnitCode(), businessQty);
            }
            String key = ProductTableNameHelper.tripleCompositeKey(itemTable, row.getDeviceDefId(), row.getLocationId());
            BigDecimal prev = acc.getOrDefault(key, BigDecimal.ZERO);
            acc.put(key, prev.add(splitQty));
        }
        return acc;
    }

    /**
     * 复制库位级预占映射，供批次遍历时原地消耗，避免改到预取批次上的原值。
     *
     * @param occupancyByItemLocationKey 表+定义+库位 → 拆零预占；可空
     * @return 可写副本；入参空时返回空 Map
     */
    public static Map<String, BigDecimal> copyOccupancyRemaining(Map<String, BigDecimal> occupancyByItemLocationKey) {
        Map<String, BigDecimal> remaining = new HashMap<>();
        if (occupancyByItemLocationKey == null || occupancyByItemLocationKey.isEmpty()) {
            // 无预占可复制时返回空映射，后续批次按账面全额可用
            return remaining;
        }
        remaining.putAll(occupancyByItemLocationKey);
        return remaining;
    }

    /**
     * 将库位级发放预占按批号行遍历顺序消耗一次，得到本批净可用拆零量。
     *
     * <p>占用聚合键为表+定义+库位、不含批号（AS-OP-DOR-003 规则 D / BR-ORD-011）。
     * 同库多批时须先扣本批能消化的预占，剩余预占留给后续批次；禁止对每一批都减去整库预占，
     * 否则账面 10+5、预占 9 会只剩 1。</p>
     *
     * @param occupancyRemainingByItemLocationKey 尚未被本轮批次消化的拆零预占；本方法原地扣减
     * @param itemTable                           物品来源表名（复合键前缀）
     * @param itemId                              物品定义主键
     * @param locationId                          库位主键
     * @param physicalSplitQuantity               本批账面拆零数量
     * @return 本批扣预占后的净可用拆零量（小于 0 时按 0）
     */
    public static BigDecimal consumeLocationOccupancyForLot(
        Map<String, BigDecimal> occupancyRemainingByItemLocationKey,
        String itemTable,
        Long itemId,
        Long locationId,
        BigDecimal physicalSplitQuantity
    ) {
        BigDecimal net = physicalSplitQuantity;
        if (net == null) {
            // 账面缺省按 0，避免空数量把后续预占算成负可用
            net = BigDecimal.ZERO;
        }
        if (occupancyRemainingByItemLocationKey == null || occupancyRemainingByItemLocationKey.isEmpty()) {
            // 无预占：净可用即账面；负账面视为 0
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            return net;
        }
        String occKey = ProductTableNameHelper.tripleCompositeKey(itemTable, itemId, locationId);
        BigDecimal locked = occupancyRemainingByItemLocationKey.get(occKey);
        if (locked == null || locked.compareTo(BigDecimal.ZERO) <= 0) {
            // 该库位预占已消化完，本批全额可用
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            return net;
        }
        // 本批先消化剩余预占：能扣多少扣多少，未消化完的留给后续批次。
        BigDecimal consume = locked.min(net);
        if (consume.compareTo(BigDecimal.ZERO) < 0) {
            consume = BigDecimal.ZERO;
        }
        occupancyRemainingByItemLocationKey.put(occKey, locked.subtract(consume));
        net = net.subtract(consume);
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            // 账面不足以覆盖本批消化量时，本批净可用为 0
            return BigDecimal.ZERO;
        }
        return net;
    }

    /**
     * 医生站签发 FIFO 分段草稿：库位、批号与发放数量。
     */
    @Getter
    @Setter
    public static class DoctorStationAdviceFifoSegmentDraft {

        /** 发放库位主键。 */
        private Long locationId;
        /** 库存批号。 */
        private String lotNumber;
        /** 本段发放数量（医嘱开立业务单位）。 */
        private BigDecimal quantity;
    }

    /**
     * 医生站签发 FIFO 分配结果：有序分段列表及仍未覆盖的拆零待分配量。
     */
    @Getter
    public static class DoctorStationAdviceFifoAllocationResult {

        /** 按 FIFO 顺序切分的发放段列表。 */
        private final List<DoctorStationAdviceFifoSegmentDraft> segments;
        /** 仍未覆盖的拆零待分配量（大于 0 表示库存不足）。 */
        private final BigDecimal remainingSplitQuantity;

        /**
         * 构造 FIFO 分配结果。
         *
         * @param segments                 有序分段列表；null 时视为空列表
         * @param remainingSplitQuantity   未覆盖的拆零量
         */
        public DoctorStationAdviceFifoAllocationResult(
            List<DoctorStationAdviceFifoSegmentDraft> segments,
            BigDecimal remainingSplitQuantity
        ) {
            if (segments == null) {
                this.segments = Collections.emptyList();
            } else {
                this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            }
            this.remainingSplitQuantity = remainingSplitQuantity;
        }
    }
}
