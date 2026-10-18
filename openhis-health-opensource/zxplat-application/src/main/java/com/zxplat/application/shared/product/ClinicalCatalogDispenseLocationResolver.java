/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.zxplat.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.MedicationCommonConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临床开嘱/目录/定价共用的取药科室模板库位解析（{@code DM-CUS-006}）。
 *
 * <p>模板 {@code distribution_category_code} 与产品目录 {@code categoryCode} 对齐（药品/耗材类别）；
 * 候选库位经 calcScore（具体项 → 种类 → 用法 → 仅发放类别）过滤后按粒度→{@code priority}→{@code id} 排序；
 * 默认库位在候选序上按 {@code BR-SLT-005} 有库存下探（1 先试，无正库存再试下一档）。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogDispenseLocationResolver {

    /** 取药科室模板读侧（候选库位与默认库位 match）。 */
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;

    /** 候选库位正库存探测（BR-SLT-005 无库存再试下一档）。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 机构模板软缓存：选品连搜时避免重复全量加载。 */
    private final ConcurrentHashMap<Long, CachedOrganizationTemplates> organizationTemplateCache =
        new ConcurrentHashMap<>();

    /**
     * 一次加载机构下全部取药科室模板行，供同请求内多产品内存解析（避免 N×findAll）。
     *
     * <p>进程内按机构短窗复用，数据量上来后连翻选品不再每次打库拉全表模板。</p>
     *
     * @param organizationId 开立/接诊科室
     * @return 机构模板实体列表；机构为空时为空列表
     */
    public List<ServiceLocationTemplate> loadOrganizationTemplateRows(Long organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        long nowMs = System.currentTimeMillis();
        CachedOrganizationTemplates cached = this.organizationTemplateCache.get(organizationId);
        if (cached != null
            && nowMs - cached.loadedAtMs() < GeneralSharedConstants.SERVICE_LOCATION_TEMPLATE_ORG_CACHE_TTL_MS) {
            return cached.rows();
        }
        List<ServiceLocationTemplate> rows =
            serviceLocationTemplateQueryService.listAllByOrganizationId(organizationId);
        List<ServiceLocationTemplate> snapshot = List.copyOf(rows);
        this.organizationTemplateCache.put(organizationId, new CachedOrganizationTemplates(snapshot, nowMs));
        return snapshot;
    }

    /**
     * 列出对当前产品适用（calcScore≥0）的候选发药库位；可选单点药房收窄。
     *
     * @param orgId                开立/接诊科室
     * @param productRow           产品读模型（表名、categoryCode、定义 id、默认用法）
     * @param pharmacyLocationId   可选指定药房
     * @return 候选库位 id 列表；机构或产品缺失时为空
     */
    public List<Long> listApplicableCandidateLocationIds(
        Long orgId,
        ProductDefinitionDisplayDto productRow,
        Long pharmacyLocationId
    ) {
        String productTableName;
        Long productDefinitionId;
        String methodCode;
        String typeCode;
        if (productRow != null) {
            productTableName = productRow.getProductTableName();
            productDefinitionId = productRow.getProductDefinitionId();
            methodCode = productRow.getMethodCode();
            typeCode = productRow.getTypeCode();
        } else {
            productTableName = null;
            productDefinitionId = null;
            methodCode = null;
            typeCode = null;
        }
        return this.listApplicableCandidateLocationIds(
            orgId,
            productTableName,
            this.resolveDistributionCategory(productRow),
            productDefinitionId,
            methodCode,
            typeCode,
            pharmacyLocationId
        );
    }

    /**
     * 按物品表 + categoryCode + 具体项 + 用法 + 种类列出适用候选库位。
     *
     * @param orgId                  开立/接诊科室
     * @param productTableName       物品主表名
     * @param categoryCode           目录 categoryCode（可空回落默认）
     * @param productDefinitionId    产品定义主键
     * @param methodCode             给药途径（耗材可空）
     * @param typeCode               耗材种类（药品可空）
     * @param pharmacyLocationId     可选指定药房
     * @return 候选库位 id 列表
     */
    public List<Long> listApplicableCandidateLocationIds(
        Long orgId,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
        String typeCode,
        Long pharmacyLocationId
    ) {
        if (orgId == null || StringUtils.isEmpty(productTableName)) {
            return List.of();
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        // 优先走机构软缓存模板 + FromLoaded，避免单品路径再 findAllByOrganizationId
        List<ServiceLocationTemplate> organizationRows = this.loadOrganizationTemplateRows(orgId);
        List<Long> locs = serviceLocationTemplateQueryService.listApplicableDistributionLocationIdsFromLoaded(
            organizationRows,
            productTableName,
            distributionCategory,
            productDefinitionId,
            methodCode,
            typeCode,
            null
        );
        return this.narrowByPharmacyLocation(locs, pharmacyLocationId);
    }

    /**
     * 基于已加载机构模板行列出适用候选库位（不再打库）。
     *
     * @param organizationRows       机构模板快照
     * @param productTableName       物品主表名
     * @param categoryCode           目录 categoryCode（可空回落默认）
     * @param productDefinitionId    产品定义主键
     * @param methodCode             给药途径
     * @param typeCode               耗材种类
     * @param pharmacyLocationId     可选指定药房
     * @return 候选库位 id 列表
     */
    public List<Long> listApplicableCandidateLocationIdsFromLoaded(
        List<ServiceLocationTemplate> organizationRows,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
        String typeCode,
        Long pharmacyLocationId
    ) {
        if (StringUtils.isEmpty(productTableName)) {
            return List.of();
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        List<Long> locs = serviceLocationTemplateQueryService.listApplicableDistributionLocationIdsFromLoaded(
            organizationRows,
            productTableName,
            distributionCategory,
            productDefinitionId,
            methodCode,
            typeCode,
            null
        );
        return this.narrowByPharmacyLocation(locs, pharmacyLocationId);
    }

    /**
     * 解析默认发药/发材库位（calcScore 择优）。
     *
     * @param orgId      开立/接诊科室
     * @param productRow 产品读模型
     * @return 默认库位；无适用规则时为 null
     */
    public Long matchDefaultDispenseLocationId(Long orgId, ProductDefinitionDisplayDto productRow) {
        if (orgId == null || productRow == null || StringUtils.isEmpty(productRow.getProductTableName())) {
            return null;
        }
        List<ServiceLocationTemplate> organizationRows = this.loadOrganizationTemplateRows(orgId);
        return this.matchDefaultDispenseLocationIdFromLoaded(organizationRows, productRow);
    }

    /**
     * 基于已加载机构模板行解析默认发药库位（不再打库）。
     *
     * @param organizationRows 机构模板快照
     * @param productRow       产品读模型
     * @return 默认库位；无适用规则或参数不全时为 null
     */
    public Long matchDefaultDispenseLocationIdFromLoaded(
        List<ServiceLocationTemplate> organizationRows,
        ProductDefinitionDisplayDto productRow
    ) {
        if (productRow == null || StringUtils.isEmpty(productRow.getProductTableName())) {
            return null;
        }
        return serviceLocationTemplateQueryService.matchDefaultDistributionLocationFromLoaded(
            organizationRows,
            productRow.getProductTableName(),
            this.resolveDistributionCategory(productRow),
            productRow.getProductDefinitionId(),
            productRow.getMethodCode(),
            productRow.getTypeCode(),
            null
        );
    }

    /**
     * 解析耗材默认发放库房（无用法维；种类取自目录 {@code typeCode}）。
     *
     * <p>先取适用候选（粒度：单品 → 种类 → 仅发放类别，再 {@code priority}→{@code id}），
     * 再按 {@code BR-SLT-005} 在候选序上有库存下探；候选均无正库存时回落序首。</p>
     *
     * @param orgId               开立/接诊科室
     * @param deviceDefinitionId  耗材定义主键
     * @param categoryCode        目录 categoryCode（可空回落默认）
     * @param typeCode            目录 typeCode（可空；空则仅命中种类通配/仅大类模板）
     * @return 默认库位；无适用规则时为 null
     */
    public Long matchDefaultDeviceDispenseLocationId(
        Long orgId,
        Long deviceDefinitionId,
        String categoryCode,
        String typeCode
    ) {
        if (orgId == null || deviceDefinitionId == null) {
            return null;
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        List<Long> candidates = this.listApplicableCandidateLocationIds(
            orgId,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            distributionCategory,
            deviceDefinitionId,
            null,
            typeCode,
            null
        );
        // 种类/单品/仅大类均未命中：与药品用法未命中且无仅大类兜底同语义
        if (candidates.isEmpty()) {
            return null;
        }
        List<InventoryCatalogAvailabilityRowModel> physicalRows =
            inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.ADM_DEVICE_DEFINITION,
                List.of(deviceDefinitionId),
                candidates
            );
        return this.pickFirstLocationWithPositiveStock(candidates, deviceDefinitionId, physicalRows);
    }

    /**
     * 在已按「匹配粒度 → priority → id」排好的候选库位序上，按 {@code BR-SLT-005} 选默认点：
     * 同档更细粒度（单品）已排在前；不同 {@code priority} 则 1 先试，该库无正库存再试下一档。
     *
     * <p>候选均无正库存时回落序首（目录仍需给前端一个模板默认点；签发 FIFO 另按库存分段）。</p>
     *
     * @param orderedCandidateLocationIds 适用候选库位有序去重列表
     * @param productDefinitionId         药品/耗材定义主键
     * @param physicalRows                已按候选库位拉取的可用库存行；可空
     * @return 默认库位；候选空时为 null
     */
    public Long pickFirstLocationWithPositiveStock(
        List<Long> orderedCandidateLocationIds,
        Long productDefinitionId,
        List<InventoryCatalogAvailabilityRowModel> physicalRows
    ) {
        if (orderedCandidateLocationIds == null || orderedCandidateLocationIds.isEmpty()) {
            return null;
        }
        // 无库存快照时无法下探，直接取模板序首（与历史 matchDefault 同语义）
        if (physicalRows == null || physicalRows.isEmpty() || productDefinitionId == null) {
            return orderedCandidateLocationIds.get(0);
        }
        for (Long locationId : orderedCandidateLocationIds) {
            // 空库位 id 不参与下探
            if (locationId == null) {
                continue;
            }
            // 本库位对该品是否存在正数量可用行
            if (this.hasPositiveStockAtLocation(physicalRows, productDefinitionId, locationId)) {
                return locationId;
            }
        }
        // 候选库均无正库存：回落模板序首，避免目录 defaultDispenseLocationId 为空
        return orderedCandidateLocationIds.get(0);
    }

    /**
     * 判断批量库存快照中指定品×库是否存在正数量可用行。
     *
     * @param physicalRows        可用库存行
     * @param productDefinitionId 品定义主键
     * @param locationId          库位主键
     * @return 存在正数量则为 true
     */
    private boolean hasPositiveStockAtLocation(
        List<InventoryCatalogAvailabilityRowModel> physicalRows,
        Long productDefinitionId,
        Long locationId
    ) {
        for (InventoryCatalogAvailabilityRowModel row : physicalRows) {
            if (row == null) {
                continue;
            }
            // 非本产品行跳过
            if (!productDefinitionId.equals(row.getItemId())) {
                continue;
            }
            // 非本库位行跳过
            if (!locationId.equals(row.getLocationId())) {
                continue;
            }
            // 正数量即视为该档有库存（与目录可用口径一致）
            if (row.getQuantity() != null && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 汇总机构取药科室模板上的全部 {@code def_location_id}（去重），供「只看库存&gt;0」分页 EXISTS 收窄。
     *
     * <p>与目录行可开立量展示同属模板库位空间；可选 {@code pharmacyLocationId} 在模板集合内时收窄为单点。
     * 不按单品 calcScore 过滤（分页前尚无当前页产品），略宽于单品候选，但仍排除药库等非模板库位账面。</p>
     *
     * @param organizationId     开立/接诊科室
     * @param pharmacyLocationId 可选指定药房
     * @return 去重库位 id；机构为空或无模板时为空列表（非 null）
     */
    public List<Long> listOrganizationTemplateDefLocationIds(Long organizationId, Long pharmacyLocationId) {
        if (organizationId == null) {
            return List.of();
        }
        List<ServiceLocationTemplate> organizationRows = this.loadOrganizationTemplateRows(organizationId);
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        for (ServiceLocationTemplate row : organizationRows) {
            if (row == null || row.getDefLocationId() == null) {
                continue;
            }
            locationIds.add(row.getDefLocationId());
        }
        return this.narrowByPharmacyLocation(new ArrayList<>(locationIds), pharmacyLocationId);
    }

    /**
     * 汇总住院科室取药模板中<strong>耗材</strong>候选库位（{@code item_table=adm_device_definition}）。
     *
     * <p>供护士划价耗材选品库存分页：按入院/当前住院科室配置的取药科室，
     * <strong>不</strong>按登录人岗位管辖药房收窄。</p>
     *
     * @param organizationId     住院科室（就诊 organizationId）
     * @param pharmacyLocationId 可选指定库房；须落在模板集合内
     * @return 去重库位 id；无模板时为空列表
     */
    public List<Long> listDeviceTemplateDefLocationIds(Long organizationId, Long pharmacyLocationId) {
        if (organizationId == null) {
            return List.of();
        }
        List<ServiceLocationTemplate> organizationRows = this.loadOrganizationTemplateRows(organizationId);
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        for (ServiceLocationTemplate row : organizationRows) {
            if (row == null || row.getDefLocationId() == null) {
                continue;
            }
            // 软删模板不参与发材库位
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            // 仅耗材表模板（排除药品取药模板）
            if (!TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getItemTable())) {
                continue;
            }
            locationIds.add(row.getDefLocationId());
        }
        return this.narrowByPharmacyLocation(new ArrayList<>(locationIds), pharmacyLocationId);
    }

    /**
     * 产品 categoryCode → 取药科室模板 distribution_category_code；空则回落默认分类常量。
     *
     * @param productRow 产品读模型
     * @return 模板分布类别 code
     */
    public String resolveDistributionCategoryCode(ProductDefinitionDisplayDto productRow) {
        return this.resolveDistributionCategory(productRow);
    }

    /**
     * 产品 categoryCode → 取药科室模板 distribution_category_code；空则回落默认分类常量。
     *
     * @param productRow 产品读模型
     * @return 模板分布类别 code
     */
    private String resolveDistributionCategory(ProductDefinitionDisplayDto productRow) {
        if (productRow == null || StringUtils.isEmpty(productRow.getCategoryCode())) {
            return MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
        }
        return productRow.getCategoryCode();
    }

    /**
     * 读侧用户指定库房且在模板候选内时收窄为单点。
     *
     * @param locs                 模板适用候选
     * @param pharmacyLocationId   可选指定药房
     * @return 收窄后的候选列表
     */
    private List<Long> narrowByPharmacyLocation(List<Long> locs, Long pharmacyLocationId) {
        if (pharmacyLocationId != null && locs.contains(pharmacyLocationId)) {
            return List.of(pharmacyLocationId);
        }
        return locs;
    }

    /**
     * 机构模板软缓存条目。
     *
     * @param rows       模板行快照
     * @param loadedAtMs 加载时刻
     */
    private record CachedOrganizationTemplates(List<ServiceLocationTemplate> rows, long loadedAtMs) {
    }
}
