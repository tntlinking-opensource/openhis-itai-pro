/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicelocationtemplate.service;

import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.openhis.health.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;
import com.openhis.health.domain.custom.servicelocationtemplate.repository.ServiceLocationTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Service;

import com.openhis.whale.core.application.util.StringUtils;

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
 *
 * <p>聚合边界：仅本聚合 {@link ServiceLocationTemplateRepository}。</p>
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
     * 列出在指定时刻、开立科室与物品表 + 类别维度下**全部生效模板行**对应的候选发药库位（不做具体项/用法 calcScore 过滤）：
     * 按 {@code priority}（数值越小越优先）、{@code id} 排序后对 {@code def_location_id} 去重；
     * <strong>不</strong>使用 {@code display_order}（仅配置页展示序，不参与发药路由）。
     * 供运维降级、护士执行等仅需「科室内该类别全部库位」的场景（AS {@code DM-CUS-006}）。
     *
     * @param organizationId 开立或接诊科室（与 {@code cus_service_location_template.organization_id} 一致）
     * @param itemTable 物品主表名（如 {@code med_medication_definition}）
     * @param distributionCategoryCode 模板 {@code distribution_category_code}，须与产品目录 {@code categoryCode} 一致
     *        （药品 {@code medication_category_code} / 耗材 {@code device_category_code}，按 {@code itemTable} 区分码表）
     * @param atTime 判定时段；空则当前时刻
     * @return 候选库房标识列表；参数不全时返回空列表
     */
    public List<Long> listCandidateDistributionLocationIds(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        LocalTime atTime
    ) {
        if (organizationId == null || StringUtils.isEmpty(itemTable) || StringUtils.isEmpty(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> effective = this.collectEffectiveTemplateRows(
            organizationId, itemTable, distributionCategoryCode, effectiveTime);
        return this.extractOrderedDefLocationIds(effective);
    }

    /**
     * 一次加载机构下全部取药科室模板行（含软删标记行，过滤由后续生效/适用逻辑完成）。
     *
     * <p>供医生站目录等同请求内对多产品反复解析候选/默认库位时复用，避免 N 次 {@code findAllByOrganizationId}。</p>
     *
     * @param organizationId 开立或接诊科室标识
     * @return 机构下模板实体列表；机构为空时返回空列表
     */
    public List<ServiceLocationTemplate> listAllByOrganizationId(Long organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return repository.findAllByOrganizationId(organizationId);
    }

    /**
     * 列出在指定时刻、开立科室与物品表 + 类别维度下、对<strong>具体物品定义与用法</strong>适用（{@link #calcScore}≥0）
     * 的候选发药库位有序列表。
     *
     * <p>排序口径与 {@link #pickBest} 一致：匹配粒度（指定物品优先于类别默认）→ {@code priority} → {@code id}；
     * <strong>不</strong>使用 {@code display_order}。供医生站 IF-DOR-11 目录、签发 FIFO 与定价 FIFO 与物理库存求交
     *（AS {@code BR-ORD-011} / {@code DM-CUS-006}）。</p>
     *
     * @param organizationId 开立或接诊科室标识
     * @param itemTable 物品主表名
     * @param distributionCategoryCode 模板 {@code distribution_category_code}，须与产品目录 {@code categoryCode} 一致
     *        （药品 {@code medication_category_code} / 耗材 {@code device_category_code}）
     * @param itemDefinitionId 具体药品/耗材定义主键；模板通配行在 calcScore 中与之匹配
     * @param methodCode 给药途径等用法编码；耗材场景通常为 null，模板 {@code method_code} 通配行仍可命中
     * @param atTime 判定时段；空则当前时刻
     * @return 适用库位标识列表；参数不全时返回空列表
     */
    public List<Long> listApplicableDistributionLocationIds(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        Long itemDefinitionId,
        String methodCode,
        LocalTime atTime
    ) {
        if (organizationId == null || StringUtils.isEmpty(itemTable) || StringUtils.isEmpty(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> effective = this.collectEffectiveTemplateRows(
            organizationId, itemTable, distributionCategoryCode, effectiveTime);
        return this.extractApplicableLocationIds(effective, itemDefinitionId, methodCode);
    }

    /**
     * 基于已加载的机构模板行解析适用候选库位（与 {@link #listApplicableDistributionLocationIds} 同口径，不再打库）。
     *
     * @param organizationRows         同机构一次加载的模板行（可含软删，本方法再滤）
     * @param itemTable                物品主表名
     * @param distributionCategoryCode 模板类别 code
     * @param itemDefinitionId         具体定义主键
     * @param methodCode               用法编码
     * @param atTime                   判定时段；空则当前时刻
     * @return 适用库位标识列表
     */
    public List<Long> listApplicableDistributionLocationIdsFromLoaded(
        List<ServiceLocationTemplate> organizationRows,
        String itemTable,
        String distributionCategoryCode,
        Long itemDefinitionId,
        String methodCode,
        LocalTime atTime
    ) {
        if (StringUtils.isEmpty(itemTable) || StringUtils.isEmpty(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> effective = this.filterEffectiveTemplateRows(
            organizationRows, itemTable, distributionCategoryCode, effectiveTime);
        return this.extractApplicableLocationIds(effective, itemDefinitionId, methodCode);
    }

    /**
     * 与 {@link #listCandidateDistributionLocationIds} 同源过滤，按 {@code priority} → {@code id} 排序后返回完整模板读模型行
     *（读模型仍携带 {@code displayOrder} 供配置展示，但本列表顺序不按展示序排）。
     *
     * @param organizationId 开立或接诊科室标识
     * @param itemTable 物品主表名
     * @param distributionCategoryCode 模板 {@code distribution_category_code}，须与产品目录 {@code categoryCode} 一致
     * @param atTime 判定时段；空则当前时刻
     * @return 模板读模型行列表
     */
    public List<ServiceLocationTemplateQueryModel> listEffectiveTemplateRowsForDispensing(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        LocalTime atTime
    ) {
        if (organizationId == null || StringUtils.isEmpty(itemTable) || StringUtils.isEmpty(distributionCategoryCode)) {
            return List.of();
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> effective = this.collectEffectiveTemplateRows(
            organizationId, itemTable, distributionCategoryCode, effectiveTime);
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
     * @param distributionCategoryCode 模板 {@code distribution_category_code}，须与产品目录 {@code categoryCode} 一致
     *        （药品 {@code medication_category_code} / 耗材 {@code device_category_code}）
     * @param itemDefinitionId 具体物品定义，可为空表示请求侧不约束具体项（仅匹配模板通配行）
     * @param methodCode 给药途径等细化条件，可为空表示请求侧不约束用法（仅匹配模板通配行）
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
        // 类别编码须与目录 categoryCode 对齐，缺失则无法与模板行过滤
        if (distributionCategoryCode == null || distributionCategoryCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "药品/耗材类别编码不能为空");
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> candidates = this.collectEffectiveTemplateRows(
            organizationId, itemTable, distributionCategoryCode, effectiveTime);
        ServiceLocationTemplate best = this.pickBest(candidates, itemDefinitionId, methodCode);
        // 无任何候选命中时调用方需自行降级（如提示人工选库位）
        if (best == null) {
            return null;
        }
        return best.getDefLocationId();
    }

    /**
     * 基于已加载的机构模板行解析默认发药库位（与 {@link #matchDefaultDistributionLocation} 同口径，不再打库）。
     *
     * @param organizationRows         同机构一次加载的模板行
     * @param itemTable                物品主表名
     * @param distributionCategoryCode 模板类别 code
     * @param itemDefinitionId         具体定义主键
     * @param methodCode               用法编码
     * @param atTime                   判定时段；空则当前时刻
     * @return 默认发药库位；无命中为 null
     */
    public Long matchDefaultDistributionLocationFromLoaded(
        List<ServiceLocationTemplate> organizationRows,
        String itemTable,
        String distributionCategoryCode,
        Long itemDefinitionId,
        String methodCode,
        LocalTime atTime
    ) {
        // 物品主表缺失则无法与模板行对齐
        if (itemTable == null || itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "物品表名不能为空");
        }
        // 类别编码缺失则无法与模板行过滤
        if (distributionCategoryCode == null || distributionCategoryCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "药品/耗材类别编码不能为空");
        }
        LocalTime effectiveTime = this.resolveTemplateEffectiveTime(atTime);
        List<ServiceLocationTemplate> candidates = this.filterEffectiveTemplateRows(
            organizationRows, itemTable, distributionCategoryCode, effectiveTime);
        ServiceLocationTemplate best = this.pickBest(candidates, itemDefinitionId, methodCode);
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
     * 按开立科室、物品表、类别与生效时段过滤模板行，并按 priority → id 排序（不按展示序）。
     *
     * @param organizationId 开立科室标识
     * @param itemTable 物品主表名
     * @param distributionCategoryCode 模板 distribution_category_code（= 目录 categoryCode）
     * @param effectiveTime 非空判定时刻
     * @return 生效模板行列表（已按业务优先级排序，可写）
     */
    private List<ServiceLocationTemplate> collectEffectiveTemplateRows(
        Long organizationId,
        String itemTable,
        String distributionCategoryCode,
        LocalTime effectiveTime
    ) {
        List<ServiceLocationTemplate> rows = repository.findAllByOrganizationId(organizationId);
        return this.filterEffectiveTemplateRows(rows, itemTable, distributionCategoryCode, effectiveTime);
    }

    /**
     * 对已加载的机构模板行做生效过滤，并按 priority → id 排序（不打库、不使用展示序）。
     *
     * @param organizationRows         机构模板行；null 视为空
     * @param itemTable                物品主表名
     * @param distributionCategoryCode 模板类别 code
     * @param effectiveTime            非空判定时刻
     * @return 生效模板行列表（已按业务优先级排序）
     */
    private List<ServiceLocationTemplate> filterEffectiveTemplateRows(
        List<ServiceLocationTemplate> organizationRows,
        String itemTable,
        String distributionCategoryCode,
        LocalTime effectiveTime
    ) {
        List<ServiceLocationTemplate> effective = new ArrayList<>();
        if (organizationRows == null || organizationRows.isEmpty()) {
            return effective;
        }
        for (ServiceLocationTemplate row : organizationRows) {
            if (row == null) {
                continue;
            }
            // 软删行不参与发药路由
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            // 当前时刻不在生效时段内则跳过
            if (!row.isEffectiveAt(effectiveTime)) {
                continue;
            }
            // 物品主表须与请求一致（药品/耗材分表）
            if (!itemTable.equals(row.getItemTable())) {
                continue;
            }
            // 发放类别须与目录 categoryCode 对齐
            if (!distributionCategoryCode.equals(row.getDistributionCategoryCode())) {
                continue;
            }
            effective.add(row);
        }
        // 业务序：优先级数值越小越先；同优先级用主键保证稳定。展示序不参与。
        effective.sort(Comparator
            .comparing(ServiceLocationTemplate::getPriority)
            .thenComparing(ServiceLocationTemplate::getId));
        return effective;
    }

    /**
     * 对已生效模板行做 calcScore≥0 过滤，再按「匹配粒度 → 优先级 → 主键」排序后提取库位 id。
     *
     * <p>保证指定物品规则排在类别默认之前，与配置页「指定物品优先于类别默认」及 {@link #pickBest} 一致；
     * 库存不足时仍可落到后续类别默认库位做 FIFO 拆分。</p>
     *
     * @param effective          已按 priority → id 排过序的生效行（本方法会按匹配粒度重排）
     * @param itemDefinitionId   具体定义主键
     * @param methodCode         用法编码
     * @return 适用库位标识列表（指定药房在前）
     */
    private List<Long> extractApplicableLocationIds(
        List<ServiceLocationTemplate> effective,
        Long itemDefinitionId,
        String methodCode
    ) {
        List<ServiceLocationTemplate> applicable = new ArrayList<>();
        for (ServiceLocationTemplate row : effective) {
            int score = this.calcScore(row, itemDefinitionId, methodCode);
            // 物品或途径与模板约束冲突时该行不进候选
            if (score < 0) {
                continue;
            }
            applicable.add(row);
        }
        // 先指定物品（分数高），再类别通配；同分看 priority，再看 id
        applicable.sort((left, right) -> this.compareApplicableRouteOrder(left, right, itemDefinitionId, methodCode));
        return this.extractOrderedDefLocationIds(applicable);
    }

    /**
     * 适用规则业务排序：匹配粒度降序 → priority 升序 → id 升序（与 {@link #pickBest} 同口径，不含展示序）。
     *
     * @param left             左侧模板行
     * @param right            右侧模板行
     * @param itemDefinitionId 请求物品定义
     * @param methodCode       请求用法
     * @return 负值表示 left 应排在 right 前
     */
    private int compareApplicableRouteOrder(
        ServiceLocationTemplate left,
        ServiceLocationTemplate right,
        Long itemDefinitionId,
        String methodCode
    ) {
        int scoreLeft = this.calcScore(left, itemDefinitionId, methodCode);
        int scoreRight = this.calcScore(right, itemDefinitionId, methodCode);
        // 分数高者（更贴脸，如指定物品）排前
        if (scoreLeft != scoreRight) {
            return Integer.compare(scoreRight, scoreLeft);
        }
        int priorityLeft = left.getPriority();
        int priorityRight = right.getPriority();
        // 同粒度时 priority 数值小者优先
        if (priorityLeft != priorityRight) {
            return Integer.compare(priorityLeft, priorityRight);
        }
        return Long.compare(left.getId(), right.getId());
    }

    /**
     * 从已按业务序排好的模板行提取 {@code def_location_id} 有序去重列表（首次出现顺序即路由先后）。
     *
     * @param orderedRows 已按业务路由序排序的模板行
     * @return 库位 id 列表
     */
    private List<Long> extractOrderedDefLocationIds(List<ServiceLocationTemplate> orderedRows) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (ServiceLocationTemplate row : orderedRows) {
            if (row.getDefLocationId() != null) {
                ordered.add(row.getDefLocationId());
            }
        }
        return new ArrayList<>(ordered);
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
}
