/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临床开嘱/目录/定价共用的取药科室模板库位解析（{@code DM-CUS-006}）。
 *
 * <p>模板 {@code distribution_category_code} 与产品目录 {@code categoryCode} 对齐（药品/耗材类别）；
 * 候选库位经 calcScore（具体项 + 用法）过滤；默认库位经 {@code matchDefaultDistributionLocation} 择优。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogDispenseLocationResolver {

    /** 取药科室模板读侧（候选库位与默认库位 match）。 */
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;

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
        if (productRow != null) {
            productTableName = productRow.getProductTableName();
            productDefinitionId = productRow.getProductDefinitionId();
            methodCode = productRow.getMethodCode();
        } else {
            productTableName = null;
            productDefinitionId = null;
            methodCode = null;
        }
        return this.listApplicableCandidateLocationIds(
            orgId,
            productTableName,
            this.resolveDistributionCategory(productRow),
            productDefinitionId,
            methodCode,
            pharmacyLocationId
        );
    }

    /**
     * 按物品表 + categoryCode + 具体项 + 用法列出适用候选库位。
     *
     * @param orgId                  开立/接诊科室
     * @param productTableName       物品主表名
     * @param categoryCode           目录 categoryCode（可空回落默认）
     * @param productDefinitionId    产品定义主键
     * @param methodCode             给药途径（耗材可空）
     * @param pharmacyLocationId     可选指定药房
     * @return 候选库位 id 列表
     */
    public List<Long> listApplicableCandidateLocationIds(
        Long orgId,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
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
     * @param pharmacyLocationId     可选指定药房
     * @return 候选库位 id 列表
     */
    public List<Long> listApplicableCandidateLocationIdsFromLoaded(
        List<ServiceLocationTemplate> organizationRows,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
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
            null
        );
    }

    /**
     * 解析耗材默认发放库房（无用法维度）。
     *
     * @param orgId               开立/接诊科室
     * @param deviceDefinitionId  耗材定义主键
     * @param categoryCode        目录 categoryCode（可空回落默认）
     * @return 默认库位；无适用规则时为 null
     */
    public Long matchDefaultDeviceDispenseLocationId(
        Long orgId,
        Long deviceDefinitionId,
        String categoryCode
    ) {
        if (orgId == null || deviceDefinitionId == null) {
            return null;
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        List<ServiceLocationTemplate> organizationRows = this.loadOrganizationTemplateRows(orgId);
        return serviceLocationTemplateQueryService.matchDefaultDistributionLocationFromLoaded(
            organizationRows,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            distributionCategory,
            deviceDefinitionId,
            null,
            null
        );
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
