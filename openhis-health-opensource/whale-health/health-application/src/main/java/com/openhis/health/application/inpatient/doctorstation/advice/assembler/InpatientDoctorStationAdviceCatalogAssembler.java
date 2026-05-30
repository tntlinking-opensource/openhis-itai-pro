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

import com.openhis.health.application.common.assembler.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceInventoryRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-DOR-11：医嘱开立目录分页装配（Asm：产品定义读模型 + 取药模板库位 ∩ 库存 + 同就诊发放预减 + 定价表选中单价）。
 * <p>复合业务键与读模型服务一致，使用 {@link GeneralSharedConstants#PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR}，避免与表名/编号中常见字符碰撞。</p>
 * <p><b>计价价型分流</b>：诊疗（{@link TableNameConstants#WKF_ACTIVITY_DEFINITION}）仅 {@link PriceType#SALE}；
 * 药品/耗材仅 {@link PriceType#LOT_NUMBER_PRICE}，且目录库存明细 {@link InpatientDoctorStationAdviceInventoryRowDto} 上单价按
 * {@code lot_no} 与库存 {@link InpatientDoctorStationAdviceInventoryRowDto#getLotNumber()} 对齐；汇总行的 {@link InpatientDoctorStationAdviceCatalogRowDto#getUnitPrice()}
 * 与首条可用库存批号一致。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceCatalogAssembler {

    /** 机构下配送/发药库位模板：解析候选库位 ID 列表。 */
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;
    /** 库存可用量：按表名+物料/定义+库位批量查询目录可开量。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 同就诊药品发放预占：目录可开量扣减。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 同就诊耗材发放预占：目录可开量扣减。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 收费项定义（定价行）：当前页批量取价。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 装配目录分页结果（读模型分页已由调用方执行一次）。
     *
     * <p><b>规模（B.2）</b>：定价批量查询与库存/预占计算均按<strong>当前页</strong>内容收敛；
     * 若调用方传入超大 {@code pageSize}，单次装配 I/O 与内存会线性放大，应由接口层限制页大小。</p>
     *
     * @param request  请求
     * @param page      产品定义分页结果
     * @param snapshot 住院就诊快照（患者/科室门控）
     * @return 当前页行
     */
    public List<InpatientDoctorStationAdviceCatalogRowDto> assemblePageContent(
        PageInpatientDoctorStationAdviceCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page,
        InpatientEncounterContextQueryModel snapshot
    ) {
        // 门控：请求患者必须与住院快照一致，防止越权用他人就诊上下文套目录数据
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }

        // Pre：当前页所有产品行的定价行一次性拉齐，避免循环内逐条打定价查询（A.9.1 批量 I/O 精神在应用层的对齐）
        Map<String, List<ChargeItemDefinition>> priceByKey = loadPricingGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            // 请求显式指定组织单元时覆盖快照机构，用于多院区模板解析
            orgId = request.getOrganizationUnitId();
        }

        String pricingSource = StringUtils.defaultIfBlank(request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        // 按实例批量取价未命中时，用读模型上的收费项定义主键再兜一条定价行（诊疗等场景常见）
        LinkedHashSet<Long> fallbackChargeDefIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto pre : content) {
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                pre.getProductTableName(), pre.getProductDefinitionId());
            List<ChargeItemDefinition> preLines = priceByKey.getOrDefault(compositeKey, List.of());
            if (selectCatalogPriceLine(pricingSource, preLines, pre.getProductTableName()) == null && pre.getChargeItemDefinitionId() != null) {
                fallbackChargeDefIds.add(pre.getChargeItemDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> fallbackPriceByDefId = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(fallbackChargeDefIds);
        List<InpatientDoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            InpatientDoctorStationAdviceCatalogRowDto dto = baseRow(productRow);
            dto.setPricingSourceCode(pricingSource);

            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())
                || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                // 药品/耗材：需要库存 ∩ 模板库位，并扣减同就诊已占用的发放数量，得到「可开量」
                List<Long> candidateLocs = resolveCandidateLocations(orgId, productRow, request.getPharmacyLocationId());
                List<Long> itemIds = List.of(productRow.getProductDefinitionId());
                List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    productRow.getProductTableName(), itemIds, candidateLocs);
                Map<String, BigDecimal> occ = loadOccupancyMap(request.getEncounterId(), productRow, candidateLocs);
                List<InpatientDoctorStationAdviceInventoryRowDto> invDtos = new ArrayList<>();
                BigDecimal sumNetSplit = BigDecimal.ZERO;
                int order = 0;
                for (InventoryCatalogAvailabilityRowModel row : physical) {
                    String k = tripleCompositeKey(row.getItemTable(), row.getItemId(), row.getLocationId());
                    BigDecimal net = row.getQuantity();
                    if (net == null) {
                        net = BigDecimal.ZERO;
                    }
                    net = net.subtract(occ.getOrDefault(k, BigDecimal.ZERO));
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    InpatientDoctorStationAdviceInventoryRowDto inv = new InpatientDoctorStationAdviceInventoryRowDto();
                    inv.setItemTable(row.getItemTable());
                    inv.setItemId(row.getItemId());
                    inv.setCategoryCode(productRow.getCategoryCode());
                    inv.setInventoryId(row.getId());
                    // 库存明细 quantity 仍为拆零单位；目录汇总 availableQuantity 在 unitBands 就绪后换算展示单位
                    inv.setQuantity(net);
                    inv.setUnitCode(productRow.getSplitUnitCode());
                    inv.setUnitName(productRow.getSplitUnitCodeDictText());
                    inv.setLotNumber(row.getLotNumber());
                    inv.setLocationId(row.getLocationId());
                    inv.setLocationStoreId(row.getLocationId());
                    inv.setDisplayOrder(order++);
                    invDtos.add(inv);
                    sumNetSplit = sumNetSplit.add(net);
                }
                dto.setInventoryList(invDtos);
                dto.setAvailableQuantity(sumNetSplit);
                if (!invDtos.isEmpty()) {
                    // 默认发点仅取首条正库存行；具体批号在 inventoryList 各行展示，不在目录行重复 default 字段。
                    InpatientDoctorStationAdviceInventoryRowDto first = invDtos.get(0);
                    dto.setDefaultDispenseLocationId(first.getLocationId());
                }
            }

            // Asm：单价来自批量定价结果；药耗有库存时按「库存批号 ↔ 定价 lot_no」逐行回填，目录行单价与首条库存批号对齐
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(productCompositeKey, List.of());
            boolean headerPriceFromInventoryLots = false;
            ChargeItemDefinition catalogPriceLine = null;
            List<InpatientDoctorStationAdviceInventoryRowDto> inventoryRows = dto.getInventoryList();
            if (isMedicationOrDeviceDefinitionTable(productRow.getProductTableName())
                && inventoryRows != null && !inventoryRows.isEmpty()) {
                Map<String, ChargeItemDefinition> lotPriceIndex = indexLotNumberPriceRows(lines);
                for (InpatientDoctorStationAdviceInventoryRowDto invRow : inventoryRows) {
                    ChargeItemDefinition rowPriceLine = resolveLotNumberPriceLineForLot(lotPriceIndex, invRow.getLotNumber());
                    if (rowPriceLine != null) {
                        invRow.setPrice(rowPriceLine.getPriceValue());
                        invRow.setChargeItemDefinitionId(rowPriceLine.getId());
                    }
                }
                InpatientDoctorStationAdviceInventoryRowDto firstInv = inventoryRows.get(0);
                ChargeItemDefinition headerLine = resolveLotNumberPriceLineForLot(lotPriceIndex, firstInv.getLotNumber());
                if (headerLine != null) {
                    dto.setChargeItemDefinitionId(headerLine.getId());
                    dto.setUnitPrice(headerLine.getPriceValue());
                    catalogPriceLine = headerLine;
                    headerPriceFromInventoryLots = true;
                }
            }
            if (!headerPriceFromInventoryLots) {
                ChargeItemDefinition selected = selectCatalogPriceLine(pricingSource, lines, productRow.getProductTableName());
                if (selected != null) {
                    dto.setChargeItemDefinitionId(selected.getId());
                    dto.setUnitPrice(selected.getPriceValue());
                    catalogPriceLine = selected;
                } else {
                    ChargeItemDefinition fallback = null;
                    if (productRow.getChargeItemDefinitionId() != null) {
                        fallback = fallbackPriceByDefId.get(productRow.getChargeItemDefinitionId());
                    }
                    if (fallback != null && isActivityDefinitionTable(productRow.getProductTableName())
                        && !PriceType.SALE.getCode().equals(fallback.getPriceTypeCode())) {
                        fallback = null;
                    }
                    if (fallback != null && isMedicationOrDeviceDefinitionTable(productRow.getProductTableName())
                        && !PriceType.LOT_NUMBER_PRICE.getCode().equals(fallback.getPriceTypeCode())) {
                        fallback = null;
                    }
                    if (fallback != null) {
                        dto.setChargeItemDefinitionId(fallback.getId());
                        dto.setUnitPrice(fallback.getPriceValue());
                        catalogPriceLine = fallback;
                    } else if (productRow.getChargeItemDefinitionId() != null) {
                        dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
                    }
                }
            }
            String catalogPriceTypeCode = null;
            if (catalogPriceLine != null) {
                catalogPriceTypeCode = catalogPriceLine.getPriceTypeCode();
            }
            String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
                productRow, catalogPriceTypeCode);
            dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, dto.getUnitPrice()));
            // 目录库存列：拆零净量换算为 PACKAGE 槽（或主计价单位）后再下发，与 unitCode / unitPrice 配套
            if (dto.getAvailableQuantity() != null) {
                dto.setAvailableQuantity(ProductDefinitionCatalogUnitBandsAssembler.convertDoctorStationCatalogAvailableQuantity(
                    productRow, dto.getUnitBands(), dto.getAvailableQuantity()));
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 从产品读模型装配目录行「与库存、定价无关」的基础展示字段（类型、名称、类别、五类单位槽、药品默认频次用法等）。
     *
     * @param productRow 当前页一条产品定义读模型
     * @return 已填充基础字段的 DTO，不含库存与定价单价
     */
    private InpatientDoctorStationAdviceCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        InpatientDoctorStationAdviceCatalogRowDto dto = new InpatientDoctorStationAdviceCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        dto.setName(productRow.getProductName());
        dto.setPyCode(productRow.getPyStr());
        dto.setWbCode(productRow.getWbStr());
        dto.setCategoryCode(productRow.getCategoryCode());
        dto.setCategoryCodeDictText(productRow.getCategoryCodeDictText());
        // 计价主单位：默认开立单位优先；药品/耗材其次采购、包装；诊疗仅包装
        String primaryUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(productRow);
        dto.setUnitCode(primaryUnitCode);
        dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(productRow));
        dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(productRow, primaryUnitCode, null));
        dto.setDefEncounterUnitCode(productRow.getDefEncounterUnitCode());
        dto.setDefEncounterUnitName(productRow.getDefEncounterUnitCodeDictText());
        dto.setDefInhospitalUnitCode(productRow.getDefInhospitalUnitCode());
        dto.setDefInhospitalUnitName(productRow.getDefInhospitalUnitCodeDictText());
        dto.setRateCode(productRow.getRateCode());
        dto.setRateCodeDictText(productRow.getRateCodeDictText());
        dto.setMethodCode(productRow.getMethodCode());
        dto.setMethodCodeDictText(productRow.getMethodCodeDictText());
        dto.setDoseFormCode(productRow.getDoseFormCode());
        dto.setDoseFormCodeDictText(productRow.getDoseFormCodeDictText());
        dto.setRestrictedFlag(productRow.getRestrictedFlag());
        dto.setRestrictedScope(productRow.getRestrictedScope());
        dto.setRestrictedEnum(productRow.getRestrictedEnum());
        dto.setRestrictedEnumDictText(productRow.getRestrictedEnumDictText());
        dto.setPartAttributeEnum(productRow.getPartAttributeEnum());
        dto.setPartAttributeEnumDictText(productRow.getPartAttributeEnumDictText());
        dto.setThoPartAttributeEnum(productRow.getThoPartAttributeEnum());
        dto.setThoPartAttributeEnumDictText(productRow.getThoPartAttributeEnumDictText());
        dto.setTotalVolume(productRow.getTotalVolume());
        dto.setSkinTestFlag(productRow.getSkinTestFlag());
        dto.setInjectFlag(productRow.getInjectFlag());
        if (StringUtils.isNotBlank(productRow.getActivityTypeCode())) {
            dto.setActivityTypeCode(productRow.getActivityTypeCode());
        }
        dto.setDefaultPerformOrgId(productRow.getLocationId());
        dto.setDefaultPerformOrgName(productRow.getLocationName());
        return dto;
    }

    /**
     * 解析发药/耗材候选库位：先按机构 + 产品表 + 配送分类取模板库位列表，若请求携带药房库位且命中模板，则收窄为该单点库位。
     *
     * @param orgId               机构或科室所属机构，用于模板查询
     * @param productRow          当前产品行（表名、分类参与模板匹配）
     * @param pharmacyLocationId 可选；前端指定「在某药房开」时传入
     * @return 候选库位 ID 列表；机构缺失时返回空列表表示不做库存展示
     */
    private List<Long> resolveCandidateLocations(Long orgId, ProductDefinitionDisplayDto productRow, Long pharmacyLocationId) {
        if (orgId == null) {
            return List.of();
        }
        String distributionCategory = StringUtils.firstNonBlank(productRow.getCategoryCode(),
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE);
        List<Long> locs = serviceLocationTemplateQueryService.listCandidateDistributionLocationIds(
            orgId, productRow.getProductTableName(), distributionCategory, null);
        if (pharmacyLocationId != null && locs.contains(pharmacyLocationId)) {
            // 用户点选药房在候选内：只返回该库位，驱动库存查询与可开量计算聚焦
            return List.of(pharmacyLocationId);
        }
        return locs;
    }

    /**
     * 构建「同就诊、同候选库位」下已占用数量映射（拆零单位），键为 {@link #tripleCompositeKey}。
     *
     * @param encounterId    当前就诊，用于筛发放记录
     * @param productRow     当前目录产品（区分药品/耗材预占查询分支及单位换算）
     * @param candidateLocs  模板解析出的库位候选；空则直接返回空映射
     * @return 复合键 → 拆零单位预占数量；无发放时为空映射
     */
    private Map<String, BigDecimal> loadOccupancyMap(Long encounterId, ProductDefinitionDisplayDto productRow, List<Long> candidateLocs) {
        Map<String, BigDecimal> acc = new LinkedHashMap<>();
        if (candidateLocs == null || candidateLocs.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())) {
            List<MedicationDispenseOrderCatalogOccupancyModel> occRows = medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                encounterId, candidateLocs, List.of(productRow.getProductDefinitionId()));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, productRow.getProductTableName());
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
            List<DeviceDispenseOrderCatalogOccupancyModel> occRows = deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                encounterId, candidateLocs, List.of(productRow.getProductDefinitionId()));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occRows, productRow, productRow.getProductTableName());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 按产品来源表分组实例 ID，一次性查询定价行，再按「表+实例」复合键分组，供当前页每行 O(1) 取价。
     *
     * @param content 当前页产品读模型集合
     * @return 复合键 → 该产品下所有定价行（顺序与查询返回一致）
     */
    private Map<String, List<ChargeItemDefinition>> loadPricingGrouped(List<ProductDefinitionDisplayDto> content) {
        // 按来源表分组实例 ID：显式 for 展开，避免 stream 分组链在审查时跳读（A.8）；体量受分页页大小约束。
        Map<String, List<Long>> idsByTable = new LinkedHashMap<>();
        for (ProductDefinitionDisplayDto row : content) {
            String table = row.getProductTableName();
            Long defId = row.getProductDefinitionId();
            if (table == null || defId == null) {
                continue;
            }
            idsByTable.computeIfAbsent(table, k -> new ArrayList<>()).add(defId);
        }
        Map<String, List<ChargeItemDefinition>> out = new HashMap<>();
        for (Map.Entry<String, List<Long>> e : idsByTable.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
            cmd.setInstanceTable(e.getKey());
            cmd.setInstanceIds(e.getValue());
            List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
            for (ChargeItemDefinition def : lines) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(def.getInstanceTable(), def.getInstanceId());
                out.computeIfAbsent(key, k -> new ArrayList<>()).add(def);
            }
        }
        return out;
    }

    /**
     * 库存可用行与发放预减行的对齐键：表名、定义或物料主键、库位三维拼接，分隔符取 {@link GeneralSharedConstants#PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR}。
     *
     * @param table        物品来源表名
     * @param definitionOrItemId 药品/耗材定义或物料主键
     * @param locationId   库位
     * @return 内存 Map 用键，禁止作为对外 API 契约暴露
     */
    private static String tripleCompositeKey(String table, Long definitionOrItemId, Long locationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return table + sep + definitionOrItemId + sep + locationId;
    }

    /**
     * 费用草稿单价来源：在已批量查出的定价行集合上按产品表语义选出一条 {@link ChargeItemDefinition}。
     * <p>诊疗（{@link TableNameConstants#WKF_ACTIVITY_DEFINITION}）仅 {@link PriceType#SALE}；
     * 药品/耗材定义表仅 {@link PriceType#LOT_NUMBER_PRICE}；其它表维持仓储顺序首条（非医生站三类目录时请慎用）。</p>
     *
     * @param instanceTable 定价实例表（与 {@link ChargeItemDefinition#getInstanceTable}、医嘱 product 锚点表一致）
     * @param lines         {@link ChargeItemDefinitionQueryService#findPriceLinesByProjects} 返回的该实例下行集合，允许含多价型
     * @return 写入费用草稿命令所用的定价行；按表分流无命中价型或集合为空时为 {@code null}
     */
    public static ChargeItemDefinition selectFirstPriceLineForChargeDraft(String instanceTable, List<ChargeItemDefinition> lines) {
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
     * 按定价来源与产品表从当前实例定价行中选出一条用于目录单价展示。
     *
     * @param pricingSourceCode {@link DoctorStationAdviceConstants#PRICING_SOURCE_RETAIL_PRICE} 或 {@link DoctorStationAdviceConstants#PRICING_SOURCE_BATCH_SELLING_PRICE}；
     *                          仅药耗目录在批次模式下于 {@link PriceType#LOT_NUMBER_PRICE} 内优先带 {@code lotNo} 的行
     * @param lines             该实例下定价行（已按查询返回顺序装入）
     * @param productTableName  产品定义来源表（物理表名）
     * @return 命中行；按表分流无命中价型时为 {@code null}
     */
    public static ChargeItemDefinition selectCatalogPriceLine(String pricingSourceCode, List<ChargeItemDefinition> lines, String productTableName) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        if (isActivityDefinitionTable(productTableName)) {
            return selectBestSalePriceLine(lines);
        }
        if (isMedicationOrDeviceDefinitionTable(productTableName)) {
            return selectMedicationDeviceCatalogPriceLine(pricingSourceCode, lines);
        }
        // 非医生站三类目录的兜底：仅在零售价（SALE）码内择优，与定价枚举口径一致。
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
     * 药品/耗材目录：仅 {@link PriceType#LOT_NUMBER_PRICE}；批次定价来源时在批号售价行内优先展示带批号的一条。
     *
     * @param pricingSourceCode 零售价或批次售价请求码
     * @param lines               当前实例下全部定价行
     * @return 择优后的批号售价行；无 {@code LOT_NUMBER_PRICE} 时为 {@code null}
     */
    private static ChargeItemDefinition selectMedicationDeviceCatalogPriceLine(
        String pricingSourceCode,
        List<ChargeItemDefinition> lines
    ) {
        String lotPriceCode = PriceType.LOT_NUMBER_PRICE.getCode();
        if (DoctorStationAdviceConstants.PRICING_SOURCE_BATCH_SELLING_PRICE.equals(pricingSourceCode)) {
            ChargeItemDefinition withLot = null;
            ChargeItemDefinition anyLotPrice = null;
            for (ChargeItemDefinition line : lines) {
                if (!lotPriceCode.equals(line.getPriceTypeCode())) {
                    continue;
                }
                anyLotPrice = betterPriceLine(anyLotPrice, line);
                if (StringUtils.isNotBlank(line.getLotNo())) {
                    withLot = betterPriceLine(withLot, line);
                }
            }
            if (withLot != null) {
                return withLot;
            }
            return anyLotPrice;
        }
        return selectBestLotNumberPriceLine(lines);
    }

    /**
     * 仅在 {@link PriceType#LOT_NUMBER_PRICE} 行内按 {@link #betterPriceLine} 择优（药耗费用草稿与目录零售价口径共用）。
     *
     * @param lines 同一实例下的全部定价行
     * @return 最优批号售价行；不存在时为 {@code null}
     */
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

    /**
     * 将 {@link PriceType#LOT_NUMBER_PRICE} 定价行按规范化批号建索引，同批号多条时按 {@link #betterPriceLine} 合并为一条。
     *
     * @param lines 当前实例下全部定价行
     * @return 规范化批号 → 择优后的批号售价行
     */
    private static Map<String, ChargeItemDefinition> indexLotNumberPriceRows(List<ChargeItemDefinition> lines) {
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
     * 批号对齐键：trim；{@code null} 与空串视为同一键。
     *
     * @param lotNo 定价或库存侧批号；{@code null} 时返回 {@link GeneralSharedConstants#EMPTY_STRING}
     * @return 用作 Map 键的规范化字符串（trim 后）
     */
    private static String normalizeLotKey(String lotNo) {
        if (lotNo == null) {
            // null 批号与空串对齐为同一 Map 键（A.4）
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNo.trim();
    }

    /**
     * 按库存行批号在索引中取对应 {@link PriceType#LOT_NUMBER_PRICE} 行。
     *
     * @param lotPriceIndex {@link #indexLotNumberPriceRows} 结果
     * @param inventoryLotNumber 库存可用行批号（与 {@code adm_charge_item_definition.lot_no} 对齐）
     * @return 命中定价行；该批号未配置批号售价时为 {@code null}
     */
    private static ChargeItemDefinition resolveLotNumberPriceLineForLot(
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
     * 判断产品定义来源表是否为诊疗活动定义（与收费定价实例表维度一致，用于 Sale-only 分流）。
     *
     * @param productTableName 读模型或命令上的物理表名；允许与常量大小写不一致
     * @return 视为诊疗活动定义则为 {@code true}
     */
    private static boolean isActivityDefinitionTable(String productTableName) {
        return productTableName != null && TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(productTableName);
    }

    /**
     * 是否为药品或耗材定义表（医生站目录三类之一），用于 {@link PriceType#LOT_NUMBER_PRICE} 与库存批号对齐。
     *
     * @param productTableName 读模型产品来源表名
     * @return 药耗定义表则为 {@code true}
     */
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

    /**
     * 在当前实例的多条定价行中仅合并 {@link PriceType#SALE}，按 {@link #betterPriceLine} 优先级择优。
     *
     * @param lines 同一实例下的全部定价行（可含 PURCHASE 等，本方法忽略非 SALE）
     * @return 最优 SALE 行；不存在 SALE 时为 {@code null}
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
     * 在两条定价行中取优先级更小者（同优先级时主键更小者），{@code null} 优先级视为较大。
     *
     * @param current 当前最优，可为 {@code null}
     * @param candidate 候选行
     * @return 更优行
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
