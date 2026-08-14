/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
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
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 临床目录读侧与暂存写路径单价解析（门诊/住院开嘱、护士划价、签发 FIFO 分段挂费）。
 *
 * <p>口径：诊疗仅 {@link PriceType#SALE}；药品/耗材有 FIFO/执行批号时必须按批号匹配
 * {@link PriceType#LOT_NUMBER_PRICE}（{@link #selectExecutionPriceLineForChargeDraft} /
 * {@link #resolveCatalogUnitPrice}）；<strong>禁止</strong>在已给定批号时跨批号
 * {@code priority}/id 择优落账。无库存且无批号时才允许批号售价择优兜底。</p>
 *
 * <p>暂存写路径可透传 {@code clientResolvedDispenseLocationId}，跳过模板二次 match。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogPriceResolver {

    /** 定价行读侧。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 库存可用行读侧。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 取药科室模板库位解析。 */
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;

    /** 药品发放占用读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 耗材发放占用读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 判断定价行在<strong>不查库存</strong>时是否已能给出目录单价。
     *
     * <p>诊疗看 {@link PriceType#SALE}；药耗看批号售价或参考零售价（无库存展示用 SALE）。
     * 供 IF-DOR-11 装配器收集 fallback，避免预扫 {@link #resolveCatalogUnitPrice} 触发 N+1。</p>
     *
     * @param productTableName 产品物理表
     * @param lines            已批量加载的定价行
     * @return true 表示无需再按定义主键兜底取价
     */
    public boolean hasResolvableCatalogPriceFromLines(String productTableName, List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty() || StringUtils.isEmpty(productTableName)) {
            return false;
        }
        // 诊疗：仅看 SALE
        if (ProductTableNameHelper.isActivity(productTableName)) {
            return selectBestSalePriceLine(lines) != null;
        }
        // 药耗：有批号售价或参考零售价即可展示（无库存时用 SALE）
        if (ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
            if (selectBestLotNumberPriceLine(lines) != null) {
                return true;
            }
            return selectBestSalePriceLine(lines) != null;
        }
        return true;
    }

    /**
     * 药耗无可用库存时的目录展示参考价：取 {@link PriceType#SALE}（零售/参考售价）。
     *
     * <p>无库存本身开不出，展示价不按批号 {@code priority} 择优，也不再二次查库存。</p>
     *
     * @param lines 同实例定价行
     * @return SALE 择优行；无则 null
     */
    public ChargeItemDefinition selectReferenceSalePriceLine(List<ChargeItemDefinition> lines) {
        return selectBestSalePriceLine(lines);
    }

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
     * 按表名分组实例 id 批量加载定价行（目录选品：按表收窄价类，避免拉进价/限价）。
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
            // 目录展示价类：诊疗仅 SALE；药耗 SALE（无库存参考）+ LOT_NUMBER_PRICE（FIFO 批号）
            cmd.setPriceTypeCodes(this.resolveCatalogPriceTypeCodes(entry.getKey()));
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
     * 目录选品按产品表解析应收窄的价类集合。
     *
     * @param productTableName 产品物理表
     * @return 价类 code 列表；未知表回落 SALE+LOT_NUMBER_PRICE
     */
    private List<String> resolveCatalogPriceTypeCodes(String productTableName) {
        if (ProductTableNameHelper.isActivity(productTableName)) {
            return List.of(PriceType.SALE.getCode());
        }
        if (ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
            return List.of(PriceType.SALE.getCode(), PriceType.LOT_NUMBER_PRICE.getCode());
        }
        return List.of(PriceType.SALE.getCode(), PriceType.LOT_NUMBER_PRICE.getCode());
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
    public ChargeItemDefinition resolveCatalogUnitPrice(ClinicalCatalogUnitPriceQuery command) {
        if (command == null) {
            return null;
        }
        List<ChargeItemDefinition> lines = command.getPriceLines();
        String productTableName = command.getProductTableName();
        if (lines == null || lines.isEmpty() || StringUtils.isEmpty(productTableName)) {
            return null;
        }
        // 诊疗目录：零售价 SALE 择优（无 FIFO / 库位维度）
        if (ProductTableNameHelper.isActivity(productTableName)) {
            return selectBestSalePriceLine(lines);
        }
        if (ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
            String lotNumber = command.getLeadingInventoryLotNumber();
            if (StringUtils.isEmpty(lotNumber)) {
                // 读/写侧：按 clientResolvedDispenseLocationId 或模板候选库位 FIFO 取首条批号
                lotNumber = findLeadingDispenseInventoryLotNumber(command);
            }
            if (StringUtils.isNotEmpty(lotNumber)) {
                Map<String, ChargeItemDefinition> lotPriceIndex = indexLotNumberPriceRows(lines);
                ChargeItemDefinition lotMatched = resolveLotNumberPriceLineForLot(lotPriceIndex, lotNumber);
                if (lotMatched != null) {
                    return lotMatched;
                }
                // 已锁定 FIFO/库存批号却无该批 LOT_NUMBER_PRICE：禁止跨批号 priority 冒充
                return null;
            }
            // 无库存批号：才允许批号售价行内择优（展示/暂存兜底）
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
     * 费用草稿单价来源（<strong>仅</strong>诊疗 SALE，或药耗<strong>无库存/无批号</strong>时的兜底）。
     *
     * <p>药耗已有 FIFO/执行批号的落账路径必须改调
     * {@link #selectExecutionPriceLineForChargeDraft}，禁止本方法跨批号 priority 择优。</p>
     *
     * @param instanceTable 定价实例表
     * @param lines         定价行集合
     * @return 写入费用草稿的定价行；无命中时 null
     */
    public ChargeItemDefinition selectFirstPriceLineForChargeDraft(String instanceTable, List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        // 诊疗：仅 SALE 择优（无批号维度）
        if (ProductTableNameHelper.isActivity(instanceTable)) {
            return selectBestSalePriceLine(lines);
        }
        // 药耗：仅无库存/无批号兜底；有 FIFO 批号时禁止调用本方法落账
        if (ProductTableNameHelper.isMedicationOrDevice(instanceTable)) {
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
        // 无显式批号：回落 FIFO 首批（与历史写路径同构）
        return resolveDraftChargePriceLine(
            productTableName,
            productDefinitionId,
            productRow,
            organizationId,
            encounterId,
            clientResolvedDispenseLocationId,
            methodCode,
            null);
    }

    /**
     * 医嘱/划价写路径单价：药耗可锁定选中批号对 {@code LOT_NUMBER_PRICE}；空批号则 FIFO。
     *
     * @param productTableName                 产品定义表
     * @param productDefinitionId              产品定义主键
     * @param productRow                       产品读模型（categoryCode / 占用换算）
     * @param organizationId                   开立机构
     * @param encounterId                      就诊（占用预减）
     * @param clientResolvedDispenseLocationId 目录下拉已解析发药库位；可空则回落模板 + FIFO
     * @param methodCode                       给药途径（药品）
     * @param leadingInventoryLotNumber        选中批号；非空时跳过 FIFO、按该批号对价
     * @return 费用草稿定价行；无命中时为 null
     */
    public ChargeItemDefinition resolveDraftChargePriceLine(
        String productTableName,
        Long productDefinitionId,
        ProductDefinitionDisplayDto productRow,
        Long organizationId,
        Long encounterId,
        Long clientResolvedDispenseLocationId,
        String methodCode,
        String leadingInventoryLotNumber
    ) {
        if (productDefinitionId == null || StringUtils.isEmpty(productTableName)) {
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
        if (ProductTableNameHelper.isActivity(productTableName)) {
            return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
        }
        if (ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
            String categoryCode = null;
            if (productRow != null) {
                categoryCode = productRow.getCategoryCode();
            }
            // 药耗：可选锁定批号；否则透传库位 + 占用预减后 FIFO
            ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(productTableName)
                .productDefinitionId(productDefinitionId)
                .categoryCode(categoryCode)
                .methodCode(methodCode)
                .organizationId(organizationId)
                .encounterId(encounterId)
                .clientResolvedDispenseLocationId(clientResolvedDispenseLocationId)
                .leadingInventoryLotNumber(leadingInventoryLotNumber)
                .priceLines(priceLines)
                .productRowForOccupancy(productRow)
                .build();
            ChargeItemDefinition catalogPrice = resolveCatalogUnitPrice(priceCmd);
            if (catalogPrice != null) {
                return catalogPrice;
            }
            // 已锁定批号却无该批价：禁止跨批号冒充（与执行落账同口径）
            if (StringUtils.isNotEmpty(leadingInventoryLotNumber)) {
                return null;
            }
            // 无 FIFO 批号可对时才允许跨批号择优兜底（BR-ORD-001 无库存）
            return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
        }
        return selectFirstPriceLineForChargeDraft(productTableName, priceLines);
    }

    /**
     * 护士执行 / 门诊签发 FIFO 分段新建 Charge：诊疗 {@link PriceType#SALE}；
     * 药耗<strong>必须</strong>按执行/FIFO 批号匹配 {@link PriceType#LOT_NUMBER_PRICE}。
     *
     * <p>批号非空却未命中定价行时返回 {@code null}（由调用方抛业务异常），
     * <strong>禁止</strong>回落其它批号的 priority 择优价，避免「批号对、价格错」。</p>
     *
     * @param instanceTable      定价实例表
     * @param lines              定价行集合
     * @param executionLotNumber 执行/FIFO 批号；药耗落账必填
     * @return 定价行；批号未命中或无可用行时 null
     */
    public ChargeItemDefinition selectExecutionPriceLineForChargeDraft(
        String instanceTable,
        List<ChargeItemDefinition> lines,
        String executionLotNumber
    ) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        // 诊疗：无批号维度，SALE 择优
        if (ProductTableNameHelper.isActivity(instanceTable)) {
            return selectBestSalePriceLine(lines);
        }
        if (ProductTableNameHelper.isMedicationOrDevice(instanceTable)) {
            Map<String, ChargeItemDefinition> lotPriceIndex = indexLotNumberPriceRows(lines);
            // 有 FIFO/执行批号：只认该批号价；未命中不跨批回落
            if (StringUtils.isNotEmpty(executionLotNumber)) {
                return resolveLotNumberPriceLineForLot(lotPriceIndex, executionLotNumber);
            }
            // 批号空：仅允许空 lot_no 定价行；再无则无库存兜底择优
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
    public String findLeadingDispenseInventoryLotNumber(ClinicalCatalogUnitPriceQuery command) {
        if (command == null || command.getOrganizationId() == null || command.getProductDefinitionId() == null) {
            return null;
        }
        String productTableName = command.getProductTableName();
        if (!ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
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
            String occKey = ProductTableNameHelper.tripleCompositeKey(row.getItemTable(), row.getItemId(), row.getLocationId());
            net = net.subtract(occupancy.getOrDefault(occKey, BigDecimal.ZERO));
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
     * @param encounterId          就诊 id（保留入参兼容；占用预减为候选库房全局口径，不再按就诊过滤）
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
        if (productRows == null || productRows.isEmpty() || StringUtils.isEmpty(productTableName)) {
            return leadingLotByProductKey;
        }
        if (!ProductTableNameHelper.isMedicationOrDevice(productTableName)) {
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
        // Pre：机构模板一次加载，避免批量 FIFO 时按产品反复 findAllByOrganizationId
        List<ServiceLocationTemplate> organizationTemplates =
            clinicalCatalogDispenseLocationResolver.loadOrganizationTemplateRows(organizationId);
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
            List<Long> candidateLocs =
                clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIdsFromLoaded(
                    organizationTemplates,
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
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
            List<MedicationDispenseOrderCatalogOccupancyModel> occupancyRows =
                medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                    candidateLocs, productDefinitionIds);
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
                    candidateLocs, productDefinitionIds);
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
                String occupancyKey = ProductTableNameHelper.tripleCompositeKey(
                    inventoryRow.getItemTable(), inventoryRow.getItemId(), inventoryRow.getLocationId());
                netQuantity = netQuantity.subtract(occupancy.getOrDefault(occupancyKey, BigDecimal.ZERO));
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
        return clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
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
    private static String resolveMethodCode(ClinicalCatalogUnitPriceQuery command) {
        if (command == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(command.getMethodCode())) {
            return command.getMethodCode();
        }
        if (command.getProductRowForOccupancy() != null) {
            return command.getProductRowForOccupancy().getMethodCode();
        }
        return null;
    }

    /**
     * 候选库位下的发放占用（拆零单位），键为表+实例+库位；不限就诊。
     */
    private Map<String, BigDecimal> loadOccupancyMap(
        String productTableName,
        Long productDefinitionId,
        ProductDefinitionDisplayDto productRowForOccupancy,
        List<Long> candidateLocs
    ) {
        if (candidateLocs == null || candidateLocs.isEmpty() || productDefinitionId == null) {
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
                    candidateLocs, List.of(productDefinitionId));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, productTableName);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTableName)) {
            List<DeviceDispenseOrderCatalogOccupancyModel> occRows =
                deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                    candidateLocs, List.of(productDefinitionId));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occRows, productRow, productTableName);
        }
        return Map.of();
    }

    /**
     * 从定价行列表中取 LOT_NUMBER_PRICE 类型最优行（priority 小优先，同 priority id 小优先）。
     *
     * <p><strong>仅</strong>用于无 FIFO/执行批号时的展示/暂存兜底，或同批号多行决胜前的类型过滤；
     * 有明确批号的<strong>落账</strong>禁止直接调用本方法冒充该批售价。</p>
     *
     * @param lines 同实例定价行
     * @return 最优批号价行；无匹配时 null
     */
    public ChargeItemDefinition selectBestLotNumberPriceLine(List<ChargeItemDefinition> lines) {
        String lotPriceCode = PriceType.LOT_NUMBER_PRICE.getCode();
        ChargeItemDefinition best = null;
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition line : lines) {
            if (line == null) {
                continue;
            }
            if (lotPriceCode.equals(line.getPriceTypeCode())) {
                best = betterPriceLine(best, line);
            }
        }
        return best;
    }

    /**
     * 批号 Map 键归一：null → 空串，非 null trim。
     *
     * @param lotNo 原始批号
     * @return Map 索引键
     */
    private static String normalizeLotKey(String lotNo) {
        if (lotNo == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNo.trim();
    }

    /**
     * 从定价行列表中取 SALE 类型最优行（priority 小优先，同 priority id 小优先）。
     *
     * @param lines 同实例定价行
     * @return 最优售价行；无匹配时 null
     */
    private static ChargeItemDefinition selectBestSalePriceLine(List<ChargeItemDefinition> lines) {
        ChargeItemDefinition best = null;
        for (ChargeItemDefinition line : lines) {
            if (PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                best = betterPriceLine(best, line);
            }
        }
        return best;
    }

    /**
     * 两条定价行择优：priority 数值小者优先；均无 priority 时 id 较小者优先。
     *
     * @param current   当前最优
     * @param candidate 候选行
     * @return 更优行；candidate 为 null 时返回 current
     */
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
