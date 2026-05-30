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

package com.openhis.health.application.common.assembler.product;

import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
     * @param productDefinitionBatchLoader 产品定义批量加载器
     * @param productTableName             产品来源表名
     * @param productDefinitionId          定义主键
     * @return 展示 DTO
     * @throws BusinessException 未命中
     */
    public static ProductDefinitionDisplayDto requireProductDefinition(
        ProductDefinitionBatchLoader productDefinitionBatchLoader,
        String productTableName,
        Long productDefinitionId
    ) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(productTableName);
        lookup.setProductDefinitionId(productDefinitionId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
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
        for (InventoryCatalogAvailabilityRowModel row : inventoryRows) {
            if (remainingSplit.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (row == null) {
                continue;
            }
            String occKey = tripleCompositeKey(itemTable, row.getItemId(), row.getLocationId());
            BigDecimal netSplit = row.getQuantity();
            if (netSplit == null) {
                netSplit = BigDecimal.ZERO;
            }
            BigDecimal locked = BigDecimal.ZERO;
            if (occupancyByItemLocationKey != null) {
                BigDecimal lockedVal = occupancyByItemLocationKey.get(occKey);
                if (lockedVal != null) {
                    locked = lockedVal;
                }
            }
            netSplit = netSplit.subtract(locked);
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
            if (StringUtils.isNotBlank(row.getUnitCode()) && productRow != null) {
                splitQty = businessQuantityToSplitUnit(productRow, row.getUnitCode(), businessQty);
            }
            String key = tripleCompositeKey(itemTable, row.getMedicationId(), row.getLocationId());
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
            if (StringUtils.isNotBlank(row.getUnitCode()) && productRow != null) {
                splitQty = businessQuantityToSplitUnit(productRow, row.getUnitCode(), businessQty);
            }
            String key = tripleCompositeKey(itemTable, row.getDeviceDefId(), row.getLocationId());
            BigDecimal prev = acc.getOrDefault(key, BigDecimal.ZERO);
            acc.put(key, prev.add(splitQty));
        }
        return acc;
    }

    /**
     * 库存预占复合键：表名、定义主键、库位。
     *
     * @param table      物品来源表名
     * @param itemId     定义主键
     * @param locationId 库位主键
     * @return 内存 Map 键
     */
    private static String tripleCompositeKey(String table, Long itemId, Long locationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return table + sep + itemId + sep + locationId;
    }
}
