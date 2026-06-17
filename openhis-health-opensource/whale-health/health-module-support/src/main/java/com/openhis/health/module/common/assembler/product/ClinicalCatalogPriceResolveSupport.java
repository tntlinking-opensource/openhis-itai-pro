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

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 临床目录读侧与暂存写路径单价解析（门诊/住院开嘱、护士划价）。
 *
 * <p>口径：诊疗仅 {@link PriceType#SALE}；药品/耗材按取药科室模板库位上 FIFO 首条可用库存批号
 * 匹配 {@link PriceType#LOT_NUMBER_PRICE}；无库存时回落批号售价择优。
 * 暂存写路径可透传 {@code clientResolvedDispenseLocationId}，跳过模板二次 match。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogPriceResolveSupport {

    /** 定价行读侧。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 库存可用行读侧。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 取药科室模板库位解析。 */
    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;

    /** 药品发放占用读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 耗材发放占用读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 按当前页产品定义批量加载定价行并按「表+实例」分组。
     *
     * @param content 产品读模型当前页
     * @return 复合键 → 定价行列表
     */
    public Map<String, List<ChargeItemDefinition>> loadPriceLinesGrouped(List<ProductDefinitionDisplayDto> content) {
        Map<String, List<Long>> idsByTable = new LinkedHashMap<>();
        if (content != null) {
            for (ProductDefinitionDisplayDto row : content) {
                if (row == null) {
                    continue;
                }
                String table = row.getProductTableName();
                Long defId = row.getProductDefinitionId();
                if (table == null || defId == null) {
                    continue;
                }
                idsByTable.computeIfAbsent(table, key -> new ArrayList<>()).add(defId);
            }
        }
        return loadPriceLinesGroupedByTable(idsByTable);
    }

    /**
     * 按表名分组实例 id 批量加载定价行。
     *
     * @param instanceIdsByTable 表 → 实例 id 列表
     * @return 复合键 → 定价行列表
     */
    public Map<String, List<ChargeItemDefinition>> loadPriceLinesGroupedByTable(
        Map<String, List<Long>> instanceIdsByTable
    ) {
        Map<String, List<ChargeItemDefinition>> grouped = new HashMap<>();
        if (instanceIdsByTable == null || instanceIdsByTable.isEmpty()) {
            return grouped;
        }
        for (Map.Entry<String, List<Long>> entry : instanceIdsByTable.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
            cmd.setInstanceTable(entry.getKey());
            cmd.setInstanceIds(entry.getValue());
            List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
            for (ChargeItemDefinition line : lines) {
                if (line == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getInstanceTable(), line.getInstanceId());
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
            }
        }
        return grouped;
    }

    /**
     * 解析目录展示单价（IF-DOR-11 / IF-IP-PRC-01 读侧；组套 IF-08 预览<strong>不</strong>调用本方法）。
     *
     * <p>药耗 FIFO 批号售价：{@code clientResolvedDispenseLocationId} 非空时仅在该库位查库存取首条批号，
     * 否则按取药科室模板解析候选库位后再 FIFO。</p>
     *
     * @param command 产品、机构、定价行及可选就诊占用 / 目录已解析库位上下文
     * @return 命中的定价行；无匹配时为 {@code null}
     */
    public ChargeItemDefinition resolveCatalogUnitPrice(ClinicalCatalogUnitPriceCommand command) {
        if (command == null) {
            return null;
        }
        List<ChargeItemDefinition> lines = command.getPriceLines();
        String productTableName = command.getProductTableName();
        if (lines == null || lines.isEmpty() || StringUtils.isBlank(productTableName)) {
            return null;
        }
        // 诊疗目录：零售价 SALE 择优（无 FIFO / 库位维度）
        if (isActivityDefinitionTable(productTableName)) {
            return selectBestSalePriceLine(lines);
        }
        if (isMedicationOrDeviceDefinitionTable(productTableName)) {
            String lotNumber = command.getLeadingInventoryLotNumber();
            if (StringUtils.isBlank(lotNumber)) {
                // 读/写侧：按 clientResolvedDispenseLocationId 或模板候选库位 FIFO 取首条批号
                lotNumber = findLeadingDispenseInventoryLotNumber(command);
            }
            if (StringUtils.isNotBlank(lotNumber)) {
                Map<String, ChargeItemDefinition> lotPriceIndex = indexLotNumberPriceRows(lines);
                ChargeItemDefinition lotMatched = resolveLotNumberPriceLineForLot(lotPriceIndex, lotNumber);
                if (lotMatched != null) {
                    return lotMatched;
                }
            }
            // 无库存批号命中时：批号售价行内择优（展示价兜底）
            return selectBestLotNumberPriceLine(lines);
        }
        // 其它实例表：回落 SALE 择优，再无则取首行
        ChargeItemDefinition saleBest = null;
        for (ChargeItemDefinition line : lines) {
            if (PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                saleBest = betterPriceLine(saleBest, line);
            }
        }
        if (saleBest != null) {
            return saleBest;
        }
        return lines.get(0);
    }

    /**
     * 费用草稿单价来源（写路径；无库存上下文时药耗仍取批号售价择优）。
     *
     * @param instanceTable 定价实例表
     * @param lines         定价行集合
     * @return 写入费用草稿的定价行
     */
    public ChargeItemDefinition selectFirstPriceLineForChargeDraft(String instanceTable, List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        if (isActivityDefinitionTable(instanceTable)) {
            return selectBestSalePriceLine(lines);
        }
        if (isMedicationOrDeviceDefinitionTable(instanceTable)) {
            return selectBestLotNumberPriceLine(lines);
        }
        return lines.get(0);
    }

    /**
     * 医嘱暂存写路径单价：药耗优先按目录已解析库位 + FIFO 批号售价；诊疗仍 {@code SALE} 择优。
     * 调用方传入 {@code clientResolvedDispenseLocationId} 时不再查取药科室模板。
     *
     * @param productTableName                 产品定义表
     * @param productDefinitionId              产品定义主键
     * @param productRow                       产品读模型（categoryCode / 占用换算）
     * @param organizationId                   开立机构
     * @param encounterId                      就诊（占用预减）
     * @param clientResolvedDispenseLocationId 目录下拉已解析发药库位；可空则回落模板 + FIFO
     * @param methodCode                       给药途径（药品）
     * @return 费用草稿定价行；无命中时为 null
     */
    public ChargeItemDefinition resolveDraftChargePriceLine(
        String productTableName,
        Long productDefinitionId,
        ProductDefinitionDisplayDto productRow,
        Long organizationId,
        Long encounterId,
        Long clientResolvedDispenseLocationId,
        String methodCode
    ) {
        if (productDefinitionId == null || StringUtils.isBlank(productTableName)) {
            return null;
        }
        // 写路径：按定义实例批量取价目行，供后续 SALE / LOT_NUMBER_PRICE 分流
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(productTableName);
        priceQuery.setInstanceIds(List.of(productDefinitionId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        if (priceLines == null || priceLines.isEmpty()) {
            return null;
        }
        // 诊疗暂存：SALE 择优，与目录读侧一致
        if (isActivityDefinitionTable(productTableName)) {
            return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
        }
        if (isMedicationOrDeviceDefinitionTable(productTableName)) {
            String categoryCode = null;
            if (productRow != null) {
                categoryCode = productRow.getCategoryCode();
            }
            // 药耗：透传目录已解析库位 + 占用预减后 FIFO 批号售价；库位空则回落模板 match
            ClinicalCatalogUnitPriceCommand priceCmd = ClinicalCatalogUnitPriceCommand.builder()
                .productTableName(productTableName)
                .productDefinitionId(productDefinitionId)
                .categoryCode(categoryCode)
                .methodCode(methodCode)
                .organizationId(organizationId)
                .encounterId(encounterId)
                .clientResolvedDispenseLocationId(clientResolvedDispenseLocationId)
                .priceLines(priceLines)
                .productRowForOccupancy(productRow)
                .build();
            ChargeItemDefinition catalogPrice = resolveCatalogUnitPrice(priceCmd);
            if (catalogPrice != null) {
                return catalogPrice;
            }
            // 无 FIFO 命中时仍取批号售价择优，避免暂存完全无价
            return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
        }
        return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
    }

    /**
     * 护士执行新建 Charge 时选取定价行：诊疗 {@link PriceType#SALE}；药耗按执行批号匹配批号售价。
     *
     * @param instanceTable      定价实例表
     * @param lines              定价行集合
     * @param executionLotNumber 执行/FIFO 批号
     * @return 定价行
     */
    public ChargeItemDefinition selectExecutionPriceLineForChargeDraft(
        String instanceTable,
        List<ChargeItemDefinition> lines,
        String executionLotNumber
    ) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        if (isActivityDefinitionTable(instanceTable)) {
            return selectBestSalePriceLine(lines);
        }
        if (isMedicationOrDeviceDefinitionTable(instanceTable)) {
            Map<String, ChargeItemDefinition> lotPriceIndex = indexLotNumberPriceRows(lines);
            if (StringUtils.isNotBlank(executionLotNumber)) {
                ChargeItemDefinition lotMatched = resolveLotNumberPriceLineForLot(lotPriceIndex, executionLotNumber);
                if (lotMatched != null) {
                    return lotMatched;
                }
            }
            ChargeItemDefinition blankLotLine = resolveLotNumberPriceLineForLot(lotPriceIndex, null);
            if (blankLotLine != null) {
                return blankLotLine;
            }
            return selectBestLotNumberPriceLine(lines);
        }
        return lines.get(0);
    }

    /**
     * 批号售价行索引（供目录多库存行逐批回填单价）。
     *
     * @param lines 同一实例下全部定价行
     * @return 规范化批号 → 择优批号售价行
     */
    public Map<String, ChargeItemDefinition> indexLotNumberPriceRows(List<ChargeItemDefinition> lines) {
        String lotPriceCode = PriceType.LOT_NUMBER_PRICE.getCode();
        Map<String, ChargeItemDefinition> index = new HashMap<>();
        if (lines == null) {
            return index;
        }
        for (ChargeItemDefinition line : lines) {
            if (!lotPriceCode.equals(line.getPriceTypeCode())) {
                continue;
            }
            String key = normalizeLotKey(line.getLotNo());
            ChargeItemDefinition existing = index.get(key);
            index.put(key, betterPriceLine(existing, line));
        }
        return index;
    }

    /**
     * 按库存批号在批号售价索引中取定价行。
     *
     * @param lotPriceIndex      批号售价索引
     * @param inventoryLotNumber 库存批号
     * @return 命中行
     */
    public ChargeItemDefinition resolveLotNumberPriceLineForLot(
        Map<String, ChargeItemDefinition> lotPriceIndex,
        String inventoryLotNumber
    ) {
        if (lotPriceIndex == null || lotPriceIndex.isEmpty()) {
            return null;
        }
        String key = normalizeLotKey(inventoryLotNumber);
        return lotPriceIndex.get(key);
    }

    /**
     * 取药科室候选库位上 FIFO 首条正可用库存的批号（效期升序 + 创建时间升序，与库存读侧一致）。
     *
     * <p>{@code clientResolvedDispenseLocationId} 非空时候选库位<strong>仅</strong>该 id（IF-DOR-14 / IF-IP-PRC 写路径透传），
     * 跳过 {@link #resolveCandidateLocations} 模板 match。</p>
     *
     * @param command 解析上下文（含目录已解析库位或模板解析所需机构/用法）
     * @return 批号；无库存时为 {@code null}
     */
    public String findLeadingDispenseInventoryLotNumber(ClinicalCatalogUnitPriceCommand command) {
        if (command == null || command.getOrganizationId() == null || command.getProductDefinitionId() == null) {
            return null;
        }
        String productTableName = command.getProductTableName();
        if (!isMedicationOrDeviceDefinitionTable(productTableName)) {
            return null;
        }
        List<Long> candidateLocs;
        if (command.getClientResolvedDispenseLocationId() != null) {
            // 写路径 / 目录已回填库位：不再查取药科室模板，直接在该库房 FIFO
            candidateLocs = List.of(command.getClientResolvedDispenseLocationId());
        } else {
            // 读路径或未传库位：按机构 + 模板 + 可选 pharmacyLocationId 收窄候选库房
            candidateLocs = resolveCandidateLocations(
                command.getOrganizationId(),
                productTableName,
                command.getCategoryCode(),
                command.getProductDefinitionId(),
                resolveMethodCode(command),
                command.getPharmacyLocationId()
            );
        }
        if (candidateLocs.isEmpty()) {
            return null;
        }
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            productTableName,
            List.of(command.getProductDefinitionId()),
            candidateLocs
        );
        Map<String, BigDecimal> occupancy = loadOccupancyMap(
            command.getEncounterId(),
            productTableName,
            command.getProductDefinitionId(),
            command.getProductRowForOccupancy(),
            candidateLocs
        );
        for (InventoryCatalogAvailabilityRowModel row : physical) {
            if (row == null) {
                continue;
            }
            BigDecimal net = row.getQuantity();
            if (net == null) {
                net = BigDecimal.ZERO;
            }
            if (command.getEncounterId() != null) {
                String occKey = tripleCompositeKey(row.getItemTable(), row.getItemId(), row.getLocationId());
                net = net.subtract(occupancy.getOrDefault(occKey, BigDecimal.ZERO));
            }
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                return row.getLotNumber();
            }
        }
        return null;
    }

    /**
     * 同一 org/就诊/库位/配送分类上下文下，批量解析药耗 FIFO 首条可用库存批号（护士划价写路径批价用）。
     *
     * @param organizationId       组织 id
     * @param encounterId          就诊 id（占用预减；可空则不做占用扣减）
     * @param pharmacyLocationId   指定发放/药房库位
     * @param productTableName     产品物理表
     * @param categoryCode         药品/耗材目录 categoryCode（模板 distribution_category_code；可空回落默认）
     * @param productRows          待解析产品读模型
     * @return 复合产品键 → FIFO 首条批号；无库存时不含该键
     */
    public Map<String, String> batchFindLeadingDispenseInventoryLotNumbers(
        Long organizationId,
        Long encounterId,
        Long pharmacyLocationId,
        String productTableName,
        String categoryCode,
        List<ProductDefinitionDisplayDto> productRows
    ) {
        Map<String, String> leadingLotByProductKey = new HashMap<>();
        if (productRows == null || productRows.isEmpty() || StringUtils.isBlank(productTableName)) {
            return leadingLotByProductKey;
        }
        if (!isMedicationOrDeviceDefinitionTable(productTableName)) {
            return leadingLotByProductKey;
        }
        List<Long> productDefinitionIds = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : productRows) {
            if (productRow != null && productRow.getProductDefinitionId() != null) {
                productDefinitionIds.add(productRow.getProductDefinitionId());
            }
        }
        if (productDefinitionIds.isEmpty()) {
            return leadingLotByProductKey;
        }
        Map<Long, List<Long>> candidateLocsByProductId = new LinkedHashMap<>();
        LinkedHashSet<Long> unionCandidateLocs = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto productRow : productRows) {
            if (productRow == null || productRow.getProductDefinitionId() == null) {
                continue;
            }
            String rowCategoryCode = StringUtils.firstNonBlank(
                productRow.getCategoryCode(),
                categoryCode,
                MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
            );
            List<Long> candidateLocs = resolveCandidateLocations(
                organizationId,
                productTableName,
                rowCategoryCode,
                productRow.getProductDefinitionId(),
                productRow.getMethodCode(),
                pharmacyLocationId
            );
            candidateLocsByProductId.put(productRow.getProductDefinitionId(), candidateLocs);
            unionCandidateLocs.addAll(candidateLocs);
        }
        if (unionCandidateLocs.isEmpty()) {
            return leadingLotByProductKey;
        }
        List<Long> candidateLocs = new ArrayList<>(unionCandidateLocs);
        // 一次批量拉取候选库位上全部产品的可用库存行（FIFO 序与单产品口径一致）
        List<InventoryCatalogAvailabilityRowModel> physicalRows =
            inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                productTableName, productDefinitionIds, candidateLocs);
        Map<Long, Map<String, BigDecimal>> occupancyByProductId = new HashMap<>();
        if (encounterId != null) {
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
                List<MedicationDispenseOrderCatalogOccupancyModel> occupancyRows =
                    medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                        encounterId, candidateLocs, productDefinitionIds);
                for (ProductDefinitionDisplayDto productRow : productRows) {
                    if (productRow == null || productRow.getProductDefinitionId() == null) {
                        continue;
                    }
                    Long productDefinitionId = productRow.getProductDefinitionId();
                    List<MedicationDispenseOrderCatalogOccupancyModel> rowsForProduct = new ArrayList<>();
                    for (MedicationDispenseOrderCatalogOccupancyModel occupancyRow : occupancyRows) {
                        if (occupancyRow != null && productDefinitionId.equals(occupancyRow.getMedicationId())) {
                            rowsForProduct.add(occupancyRow);
                        }
                    }
                    occupancyByProductId.put(
                        productDefinitionId,
                        DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                            rowsForProduct, productRow, productTableName));
                }
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTableName)) {
                List<DeviceDispenseOrderCatalogOccupancyModel> occupancyRows =
                    deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                        encounterId, candidateLocs, productDefinitionIds);
                for (ProductDefinitionDisplayDto productRow : productRows) {
                    if (productRow == null || productRow.getProductDefinitionId() == null) {
                        continue;
                    }
                    Long productDefinitionId = productRow.getProductDefinitionId();
                    List<DeviceDispenseOrderCatalogOccupancyModel> rowsForProduct = new ArrayList<>();
                    for (DeviceDispenseOrderCatalogOccupancyModel occupancyRow : occupancyRows) {
                        if (occupancyRow != null && productDefinitionId.equals(occupancyRow.getDeviceDefId())) {
                            rowsForProduct.add(occupancyRow);
                        }
                    }
                    occupancyByProductId.put(
                        productDefinitionId,
                        DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                            rowsForProduct, productRow, productTableName));
                }
            }
        }
        for (ProductDefinitionDisplayDto productRow : productRows) {
            if (productRow == null || productRow.getProductDefinitionId() == null) {
                continue;
            }
            Long productDefinitionId = productRow.getProductDefinitionId();
            List<Long> productCandidateLocs = candidateLocsByProductId.getOrDefault(productDefinitionId, List.of());
            if (productCandidateLocs.isEmpty()) {
                continue;
            }
            Map<String, BigDecimal> occupancy = occupancyByProductId.getOrDefault(productDefinitionId, Map.of());
            for (InventoryCatalogAvailabilityRowModel inventoryRow : physicalRows) {
                if (inventoryRow == null || !productDefinitionId.equals(inventoryRow.getItemId())) {
                    continue;
                }
                if (!productCandidateLocs.contains(inventoryRow.getLocationId())) {
                    continue;
                }
                BigDecimal netQuantity = inventoryRow.getQuantity();
                if (netQuantity == null) {
                    netQuantity = BigDecimal.ZERO;
                }
                if (encounterId != null) {
                    String occupancyKey = tripleCompositeKey(
                        inventoryRow.getItemTable(), inventoryRow.getItemId(), inventoryRow.getLocationId());
                    netQuantity = netQuantity.subtract(occupancy.getOrDefault(occupancyKey, BigDecimal.ZERO));
                }
                if (netQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        productTableName, productDefinitionId);
                    leadingLotByProductKey.put(productKey, inventoryRow.getLotNumber());
                    break;
                }
            }
        }
        return leadingLotByProductKey;
    }

    /**
     * 解析发药/耗材候选库位：模板 categoryCode 维度 + 具体项/用法 calcScore 过滤，可选单点药房收窄。
     */
    private List<Long> resolveCandidateLocations(
        Long organizationId,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
        Long pharmacyLocationId
    ) {
        return clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
            organizationId,
            productTableName,
            categoryCode,
            productDefinitionId,
            methodCode,
            pharmacyLocationId
        );
    }

    /**
     * 从单价解析命令取用法编码：显式字段优先，否则回落产品读模型默认用法。
     */
    private static String resolveMethodCode(ClinicalCatalogUnitPriceCommand command) {
        if (command == null) {
            return null;
        }
        if (StringUtils.isNotBlank(command.getMethodCode())) {
            return command.getMethodCode();
        }
        if (command.getProductRowForOccupancy() != null) {
            return command.getProductRowForOccupancy().getMethodCode();
        }
        return null;
    }

    /**
     * 同就诊、同候选库位下的发放占用（拆零单位），键为表+实例+库位。
     */
    private Map<String, BigDecimal> loadOccupancyMap(
        Long encounterId,
        String productTableName,
        Long productDefinitionId,
        ProductDefinitionDisplayDto productRowForOccupancy,
        List<Long> candidateLocs
    ) {
        if (encounterId == null || candidateLocs == null || candidateLocs.isEmpty() || productDefinitionId == null) {
            return Map.of();
        }
        ProductDefinitionDisplayDto productRow = productRowForOccupancy;
        if (productRow == null) {
            productRow = new ProductDefinitionDisplayDto();
            productRow.setProductTableName(productTableName);
            productRow.setProductDefinitionId(productDefinitionId);
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
            List<MedicationDispenseOrderCatalogOccupancyModel> occRows =
                medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                    encounterId, candidateLocs, List.of(productDefinitionId));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, productTableName);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTableName)) {
            List<DeviceDispenseOrderCatalogOccupancyModel> occRows =
                deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                    encounterId, candidateLocs, List.of(productDefinitionId));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occRows, productRow, productTableName);
        }
        return Map.of();
    }

    private static String tripleCompositeKey(String table, Long definitionOrItemId, Long locationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return table + sep + definitionOrItemId + sep + locationId;
    }

    private static ChargeItemDefinition selectBestLotNumberPriceLine(List<ChargeItemDefinition> lines) {
        String lotPriceCode = PriceType.LOT_NUMBER_PRICE.getCode();
        ChargeItemDefinition best = null;
        for (ChargeItemDefinition line : lines) {
            if (lotPriceCode.equals(line.getPriceTypeCode())) {
                best = betterPriceLine(best, line);
            }
        }
        return best;
    }

    private static String normalizeLotKey(String lotNo) {
        if (lotNo == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNo.trim();
    }

    private static boolean isActivityDefinitionTable(String productTableName) {
        return productTableName != null
            && TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(productTableName);
    }

    private static boolean isMedicationOrDeviceDefinitionTable(String productTableName) {
        if (productTableName == null) {
            return false;
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(productTableName)) {
            return true;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(productTableName)) {
            return true;
        }
        return false;
    }

    private static ChargeItemDefinition selectBestSalePriceLine(List<ChargeItemDefinition> lines) {
        ChargeItemDefinition best = null;
        for (ChargeItemDefinition line : lines) {
            if (PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                best = betterPriceLine(best, line);
            }
        }
        return best;
    }

    private static ChargeItemDefinition betterPriceLine(ChargeItemDefinition current, ChargeItemDefinition candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        Integer cp = candidate.getPriority();
        Integer curp = current.getPriority();
        if (cp != null && curp != null) {
            int cmp = Integer.compare(cp, curp);
            if (cmp < 0) {
                return candidate;
            }
            if (cmp > 0) {
                return current;
            }
        } else if (cp != null) {
            return candidate;
        } else if (curp != null) {
            return current;
        }
        Long cid = candidate.getId();
        Long curid = current.getId();
        if (cid != null && curid != null && cid < curid) {
            return candidate;
        }
        return current;
    }
}
