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

package com.openhis.health.domain.custom.servicelocationtemplate.service;

import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.openhis.health.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;
import com.openhis.health.domain.custom.servicelocationtemplate.repository.ServiceLocationTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Service;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 取药/发药科室路由模板读侧查询服务。
 *
 * <p>职责：维护「开立/接诊科室 + 物品维度 + 时段」到默认发药库位的规则解析，供摆药与发药路径推荐及医生站目录与库存求交；仅查询不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceLocationTemplateQueryService {

    private final ServiceLocationTemplateRepository repository;

    /**
     * 按开立科室列出该机构下全部路由规则（按优先级与主键排序，供配置页展示）。
     *
     * @param organizationId 开立科室（组织）标识
     * @return 规则读模型列表
     */
    public List<ServiceLocationTemplateQueryModel> listDistributionRulesByOrganization(Long organizationId) {
        List<ServiceLocationTemplate> rows = repository.findAllByOrganizationId(organizationId);
        // 同科室内多规则时优先展示高优先级（数值小者优先，与业务约定一致）
        rows.sort(Comparator.comparing(ServiceLocationTemplate::getPriority).thenComparing(ServiceLocationTemplate::getId));
        List<ServiceLocationTemplateQueryModel> result = new ArrayList<>();
        for (ServiceLocationTemplate row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 全机构规则一览（不分页，用于运维或总览场景）。
     *
     * @return 全部规则读模型，按机构、优先级、主键排序
     */
    public List<ServiceLocationTemplateQueryModel> listAllDistributionRules() {
        List<ServiceLocationTemplate> rows = repository.findAllByOrderByOrganizationIdAscPriorityAscIdAsc();
        List<ServiceLocationTemplateQueryModel> result = new ArrayList<>();
        for (ServiceLocationTemplate row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 列出在指定时刻、接诊/开立科室与物品维度下**候选发药库位**（模板 {@code def_location_id}）的稳定有序列表：
     * 先按 {@code display_order}（空视为最大）、{@code priority}、{@code id} 排序模板行，再对库位去重保持首次出现顺序。
     * 供医生站 IF-DOR-11 与物理库存求交（AS {@code BR-ORD-011} / {@code DM-CUS-006}）。
     *
     * @param organizationId 开立或接诊科室（与 {@code cus_service_location_template.organization_id} 一致）
     * @param itemTable 物品主表名（如 {@code med_medication_definition}）
     * @param distributionCategoryCode 发放类别编码（与模板行一致；目录装配侧由药品/耗材分类映射传入）
     * @param atTime 判定时段；空则当前时刻
     * @return 候选库房标识列表；参数不全时返回空列表
     */
    public List<Long> listCandidateDistributionLocationIds(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        LocalTime atTime
    ) {
        if (organizationId == null || StringUtils.isBlank(itemTable) || StringUtils.isBlank(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> rows = repository.findAllByOrganizationId(organizationId);
        List<ServiceLocationTemplate> effective = new ArrayList<>();
        for (ServiceLocationTemplate row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!row.isEffectiveAt(effectiveTime)) {
                continue;
            }
            if (!itemTable.equals(row.getItemTable())) {
                continue;
            }
            if (!distributionCategoryCode.equals(row.getDistributionCategoryCode())) {
                continue;
            }
            effective.add(row);
        }
        effective.sort(Comparator
            .comparing(ServiceLocationTemplateQueryService::displayOrderSortKey)
            .thenComparing(ServiceLocationTemplate::getPriority)
            .thenComparing(ServiceLocationTemplate::getId));
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (ServiceLocationTemplate row : effective) {
            if (row.getDefLocationId() != null) {
                ordered.add(row.getDefLocationId());
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 与 {@link #listCandidateDistributionLocationIds} 同源过滤与排序，返回完整模板读模型行（含 {@code displayOrder} 等），供装配器降级逻辑使用。
     *
     * @param organizationId 开立或接诊科室标识
     * @param itemTable 物品主表名
     * @param distributionCategoryCode 发放类别编码
     * @param atTime 判定时段；空则当前时刻
     * @return 模板读模型行列表
     */
    public List<ServiceLocationTemplateQueryModel> listEffectiveTemplateRowsForDispensing(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        LocalTime atTime
    ) {
        if (organizationId == null || StringUtils.isBlank(itemTable) || StringUtils.isBlank(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> rows = repository.findAllByOrganizationId(organizationId);
        List<ServiceLocationTemplate> effective = new ArrayList<>();
        for (ServiceLocationTemplate row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!row.isEffectiveAt(effectiveTime)) {
                continue;
            }
            if (!itemTable.equals(row.getItemTable())) {
                continue;
            }
            if (!distributionCategoryCode.equals(row.getDistributionCategoryCode())) {
                continue;
            }
            effective.add(row);
        }
        effective.sort(Comparator
            .comparing(ServiceLocationTemplateQueryService::displayOrderSortKey)
            .thenComparing(ServiceLocationTemplate::getPriority)
            .thenComparing(ServiceLocationTemplate::getId));
        List<ServiceLocationTemplateQueryModel> result = new ArrayList<>();
        for (ServiceLocationTemplate row : effective) {
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 在给定时刻解析默认发药库位：命中多条时按精确度与优先级择优，无命中返回 {@code null}。
     *
     * @param organizationId 开立/接诊科室标识
     * @param itemTable 物品主表名
     * @param distributionCategoryCode 发放类别（如口服/注射等维度）
     * @param itemDefinitionId 具体物品定义，可为空表示通配
     * @param methodCode 给药途径等细化条件，可为空表示通配
     * @param atTime 判定生效时段的时刻；为空则取当前时刻
     * @return 默认发药库位标识；无适用规则时为 null
     */
    public Long matchDefaultDistributionLocation(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        Long itemDefinitionId,
        String methodCode,
        LocalTime atTime
    ) {
        // 无开立科室无法界定规则归属，直接拒绝避免静默落到错误库位
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        // 物品主表是规则维度之一，缺失则无法与模板行对齐
        if (itemTable == null || itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "物品表名不能为空");
        }
        // 发放类别决定走哪条业务分支（与模板行过滤一致）
        if (distributionCategoryCode == null || distributionCategoryCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "发放类别编码不能为空");
        }
        // 未传时刻则按「当前」判断是否落在模板的起止时段内
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        List<ServiceLocationTemplate> rows = repository.findAllByOrganizationId(organizationId);
        List<ServiceLocationTemplate> candidates = new ArrayList<>();
        for (ServiceLocationTemplate row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            // 规则未在该时刻生效则跳过（如夜间不开放的发药窗口）
            if (!row.isEffectiveAt(atTime)) {
                continue;
            }
            // 物品主表不一致则本行不适用
            if (!itemTable.equals(row.getItemTable())) {
                continue;
            }
            // 发放类别不一致则本行不适用
            if (!distributionCategoryCode.equals(row.getDistributionCategoryCode())) {
                continue;
            }
            candidates.add(row);
        }
        ServiceLocationTemplate best = this.pickBest(candidates, itemDefinitionId, methodCode);
        // 无任何候选命中时调用方需自行降级（如提示人工选库位）
        if (best == null) {
            return null;
        }
        return best.getDefLocationId();
    }

    /**
     * 在已通过表名/类别/时段过滤的候选中，按「匹配分数 → 优先级 → 主键」选出唯一最优规则。
     *
     * @param candidates 候选模板行
     * @param itemDefinitionId 请求侧物品定义，用于与模板通配/精确比对
     * @param methodCode 请求侧途径编码，用于与模板通配/精确比对
     * @return 最优模板行；候选全不匹配（分数为负）时返回 null
     */
    private ServiceLocationTemplate pickBest(List<ServiceLocationTemplate> candidates, Long itemDefinitionId, String methodCode) {
        ServiceLocationTemplate best = null;
        for (ServiceLocationTemplate row : candidates) {
            int score = this.calcScore(row, itemDefinitionId, methodCode);
            // 物品与途径组合与模板约束冲突时该行无效
            if (score < 0) {
                continue;
            }
            // 首个有效候选直接作为暂优
            if (best == null) {
                best = row;
                continue;
            }
            int bestScore = this.calcScore(best, itemDefinitionId, methodCode);
            // 更高匹配粒度覆盖较低者（例如精确物品+途径优于通配）
            if (score > bestScore) {
                best = row;
                continue;
            }
            // 同分时优先采用模板配置的优先级数值更小者
            if (score == bestScore && row.getPriority() < best.getPriority()) {
                best = row;
                continue;
            }
            // 优先级仍相同则主键较小者胜出，保证结果确定、可复现
            if (score == bestScore && row.getPriority().equals(best.getPriority()) && row.getId() < best.getId()) {
                best = row;
            }
        }
        return best;
    }

    /**
     * 计算单条模板与当前请求的组合匹配分：分值越高表示约束越「贴脸」，负分表示不兼容。
     *
     * @param row 模板行
     * @param itemDefinitionId 请求物品定义标识
     * @param methodCode 请求途径编码
     * @return 4=物品+途径均精确；3/2=其一通配；1=双通配；-1=冲突
     */
    private int calcScore(ServiceLocationTemplate row, Long itemDefinitionId, String methodCode) {
        boolean itemMatched = itemDefinitionId != null && itemDefinitionId.equals(row.getItemDefinitionId());
        boolean methodMatched = methodCode != null && methodCode.equals(row.getMethodCode());
        boolean itemWildcard = row.getItemDefinitionId() == null;
        boolean methodWildcard = row.getMethodCode() == null;

        // 物品与途径均与模板精确一致，最可信
        if (itemMatched && methodMatched) {
            return 4;
        }
        // 物品精确、途径由模板通配承接
        if (itemMatched && methodWildcard) {
            return 3;
        }
        // 物品通配、途径精确命中
        if (itemWildcard && methodMatched) {
            return 2;
        }
        // 双维度均通配，兜底命中但弱于上述情形
        if (itemWildcard && methodWildcard) {
            return 1;
        }
        return -1;
    }

    /**
     * 实体字段拷贝到读模型，供配置与调试展示。
     *
     * @param row 模板实体
     * @return 查询模型
     */
    private ServiceLocationTemplateQueryModel toQueryModel(ServiceLocationTemplate row) {
        ServiceLocationTemplateQueryModel model = new ServiceLocationTemplateQueryModel();
        model.setId(row.getId());
        model.setOrganizationId(row.getOrganizationId());
        model.setItemTable(row.getItemTable());
        model.setDistributionCategoryCode(row.getDistributionCategoryCode());
        model.setDefLocationId(row.getDefLocationId());
        model.setItemDefinitionId(row.getItemDefinitionId());
        model.setMethodCode(row.getMethodCode());
        model.setStartTime(row.getStartTime());
        model.setEndTime(row.getEndTime());
        model.setPriority(row.getPriority());
        model.setDisplayOrder(row.getDisplayOrder());
        model.setRemark(row.getRemark());
        return model;
    }

    /**
     * 模板时段判定使用的时间点：调用方未传则取「当前」，与 {@link #matchDefaultDistributionLocation} 口径一致。
     *
     * @param atTime 业务传入判定时刻，可为空
     * @return 非空的生效时刻
     */
    private LocalTime resolveTemplateEffectiveTime(LocalTime atTime) {
        if (atTime == null) {
            return LocalTime.now();
        }
        return atTime;
    }

    /**
     * {@code display_order} 列用于排序的整型键；未配置时使用 {@link Integer#MAX_VALUE}，使未配行在升序比较中稳定靠后。
     *
     * @param row 模板行
     * @return 排序键
     */
    private static int displayOrderSortKey(ServiceLocationTemplate row) {
        if (row.getDisplayOrder() == null) {
            return Integer.MAX_VALUE;
        }
        return row.getDisplayOrder();
    }
}

