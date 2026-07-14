/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.service;

import com.openhis.health.domain.custom.activityexecutionlocation.entity.ActivityExecutionLocation;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleBatchHitQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleHitQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.repository.ActivityExecutionLocationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行科室规则读侧查询服务。
 *
 * <p>职责：按诊疗定义解析「在什么时间由哪个科室/地点执行」的规则列表与单条命中，支持批量评核。</p>
 *
 * <p>边界：只读；锚点主键缺失或批量入参为空时抛 {@link BusinessException}；无规则覆盖查询时刻时返回 {@code null} 或空映射项由调用方处理。</p>
 *
 * <p>聚合边界：仅本聚合 {@link ActivityExecutionLocationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityExecutionLocationQueryService {

    private final ActivityExecutionLocationRepository repository;

    /**
     * 按诊疗定义查询规则列表（按优先级升序、id 升序稳定排序；1 最优先）。
     *
     * @param activityDefinitionId 诊疗定义标识
     * @return 规则列表
     * @throws BusinessException 诊疗定义标识为空
     */
    public List<ExecutionRuleListQueryModel> listExecutionRulesByActivityDefinition(Long activityDefinitionId) {
        return this.listExecutionRulesForActivityResolution(activityDefinitionId, null);
    }

    /**
     * 按诊疗定义解析可命中的执行规则：项目级规则 + 同大类默认规则（未配具体项目）。
     *
     * <p>医生站开立诊疗时消费：先返回绑定该项目的规则，再叠加 {@code activity_definition_id} 为空的大类默认规则。</p>
     *
     * @param activityDefinitionId 诊疗定义标识
     * @param activityCategoryCode 活动类别编码；为空时仅返回项目级规则
     * @return 合并去重后的规则列表
     * @throws BusinessException 诊疗定义标识为空
     */
    public List<ExecutionRuleListQueryModel> listExecutionRulesForActivityResolution(
        Long activityDefinitionId,
        String activityCategoryCode
    ) {
        if (activityDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识不能为空");
        }
        Map<Long, ActivityExecutionLocation> uniqueRules = new HashMap<>();
        for (ActivityExecutionLocation rule : repository.findAllByActivityDefinitionId(activityDefinitionId)) {
            if (rule.getId() != null) {
                uniqueRules.put(rule.getId(), rule);
            }
        }
        if (activityCategoryCode != null && !activityCategoryCode.trim().isEmpty()) {
            for (ActivityExecutionLocation rule :
                repository.findAllByActivityCategoryCodeAndActivityDefinitionIdIsNull(activityCategoryCode.trim())) {
                if (rule.getId() != null) {
                    uniqueRules.put(rule.getId(), rule);
                }
            }
        }
        List<ActivityExecutionLocation> rules = new ArrayList<>(uniqueRules.values());
        this.sortRulesForDisplay(rules);
        List<ExecutionRuleListQueryModel> result = new ArrayList<>();
        for (ActivityExecutionLocation rule : rules) {
            result.add(this.toListModel(rule));
        }
        return result;
    }

    /**
     * 按执行科室查询规则列表（可按活动类别过滤）。
     *
     * @param executionOrgId 执行科室标识
     * @param activityCategoryCode 活动类别编码（可空）
     * @return 规则列表
     */
    public List<ExecutionRuleListQueryModel> listExecutionRulesByExecutionOrg(
        Long executionOrgId,
        String activityCategoryCode
    ) {
        if (executionOrgId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
                "执行科室标识不能为空");
        }
        List<ActivityExecutionLocation> rules;
        if (activityCategoryCode == null || activityCategoryCode.trim().isEmpty()) {
            rules = repository.findAllByExecutionOrgId(executionOrgId);
        } else {
            rules = repository.findAllByExecutionOrgIdAndActivityCategoryCode(
                executionOrgId, activityCategoryCode.trim());
        }
        this.sortRulesForDisplay(rules);
        List<ExecutionRuleListQueryModel> result = new ArrayList<>();
        for (ActivityExecutionLocation rule : rules) {
            result.add(this.toListModel(rule));
        }
        return result;
    }

    /**
     * 按诊疗定义与时刻解析单条最优先命中规则。
     *
     * <p>优先匹配项目级规则；无命中且提供活动类别时，再回退到大类默认规则（未配具体项目）。</p>
     *
     * @param activityDefinitionId 诊疗定义标识
     * @param activityCategoryCode 活动类别编码；可空
     * @param atTime               评核时刻；{@code null} 时使用当前时刻
     * @return 命中规则；无命中时 {@code null}
     * @throws BusinessException 诊疗定义标识为空
     */
    public ExecutionRuleHitQueryModel findByActivityAndTime(
        Long activityDefinitionId,
        String activityCategoryCode,
        LocalTime atTime
    ) {
        if (activityDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识不能为空");
        }
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        List<ActivityExecutionLocation> rules = repository.findAllByActivityDefinitionId(activityDefinitionId);
        ActivityExecutionLocation hit = this.findTopHit(rules, atTime);
        if (hit == null && activityCategoryCode != null && !activityCategoryCode.trim().isEmpty()) {
            List<ActivityExecutionLocation> categoryRules =
                repository.findAllByActivityCategoryCodeAndActivityDefinitionIdIsNull(activityCategoryCode.trim());
            hit = this.findTopHit(categoryRules, atTime);
        }
        if (hit == null) {
            return null;
        }
        return this.toHitModel(hit, activityDefinitionId);
    }

    /**
     * 按诊疗定义与时刻解析单条最优先命中规则（仅项目级规则，不含大类默认回退）。
     *
     * @param activityDefinitionId 诊疗定义标识
     * @param atTime               评核时刻；{@code null} 时使用当前时刻
     * @return 命中规则；无命中时 {@code null}
     * @throws BusinessException 诊疗定义标识为空
     */
    public ExecutionRuleHitQueryModel findByActivityAndTime(Long activityDefinitionId, LocalTime atTime) {
        return this.findByActivityAndTime(activityDefinitionId, null, atTime);
    }

    /**
     * 批量按诊疗定义与时刻解析各锚点的最优先命中规则。
     *
     * @param activityDefinitionIds 诊疗定义标识集合
     * @param atTime                评核时刻；{@code null} 时使用当前时刻
     * @return 锚点到批量命中模型映射
     * @throws BusinessException 集合为空
     */
    public Map<Long, ExecutionRuleBatchHitQueryModel> findForBatchByActivityAndTime(
        List<Long> activityDefinitionIds,
        LocalTime atTime
    ) {
        if (activityDefinitionIds == null || activityDefinitionIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识集合不能为空");
        }
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        List<ActivityExecutionLocation> rules = repository.findAllByActivityDefinitionIdIn(activityDefinitionIds);
        return this.groupTopHitsByActivityDefinition(rules, atTime);
    }

    /**
     * 列表展示排序：优先级升序（1 最优先），其次 display_order，再 id。
     *
     * @param rules 待排序规则
     */
    private void sortRulesForDisplay(List<ActivityExecutionLocation> rules) {
        rules.sort(Comparator
            .comparing(ActivityExecutionLocation::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ActivityExecutionLocation::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ActivityExecutionLocation::getId));
    }

    /**
     * 将实体转换为列表查询模型。
     *
     * @param rule 规则实体
     * @return 列表查询模型
     */
    private ExecutionRuleListQueryModel toListModel(ActivityExecutionLocation rule) {
        ExecutionRuleListQueryModel model = new ExecutionRuleListQueryModel();
        model.setId(rule.getId());
        model.setActivityDefinitionId(rule.getActivityDefinitionId());
        model.setActivityCategoryCode(rule.getActivityCategoryCode());
        model.setExecutionOrgId(rule.getExecutionOrgId());
        model.setExecutionLocationId(rule.getExecutionLocationId());
        model.setStartTime(rule.getStartTime());
        model.setEndTime(rule.getEndTime());
        model.setPriority(rule.getPriority());
        model.setDisplayOrder(rule.getDisplayOrder());
        model.setRemark(rule.getRemark());
        return model;
    }

    /**
     * 将实体转换为命中查询模型。
     *
     * @param rule     规则实体
     * @param anchorId 诊疗定义锚点 id
     * @return 命中查询模型
     */
    private ExecutionRuleHitQueryModel toHitModel(ActivityExecutionLocation rule, Long anchorId) {
        ExecutionRuleHitQueryModel model = new ExecutionRuleHitQueryModel();
        model.setRuleId(rule.getId());
        model.setAnchorId(anchorId);
        model.setExecutionOrgId(rule.getExecutionOrgId());
        model.setExecutionLocationId(rule.getExecutionLocationId());
        model.setActivityCategoryCode(rule.getActivityCategoryCode());
        model.setStartTime(rule.getStartTime());
        model.setEndTime(rule.getEndTime());
        model.setPriority(rule.getPriority());
        return model;
    }

    /**
     * 在候选集合中按时间窗和优先级选取命中规则。
     *
     * @param rules  候选规则
     * @param atTime 查询时刻
     * @return 命中规则；无命中时 {@code null}
     */
    private ActivityExecutionLocation findTopHit(List<ActivityExecutionLocation> rules, LocalTime atTime) {
        ActivityExecutionLocation best = null;
        for (ActivityExecutionLocation rule : rules) {
            if (!this.hitAt(rule, atTime)) {
                continue;
            }
            if (best == null) {
                best = rule;
                continue;
            }
            // 同刻多规则并存时取优先级更高者（数值小优先，与取药科室一致）。
            if (this.isHigherPriority(rule, best)) {
                best = rule;
            }
        }
        return best;
    }

    /**
     * 批量按诊疗定义分组选取命中规则（每个锚点保留一条最优规则）。
     *
     * @param rules  候选规则
     * @param atTime 查询时刻
     * @return 锚点到批量命中模型映射
     */
    private Map<Long, ExecutionRuleBatchHitQueryModel> groupTopHitsByActivityDefinition(
        List<ActivityExecutionLocation> rules,
        LocalTime atTime
    ) {
        Map<Long, ActivityExecutionLocation> bestMap = new HashMap<>();
        for (ActivityExecutionLocation rule : rules) {
            if (!this.hitAt(rule, atTime)) {
                continue;
            }
            Long anchorId = rule.getActivityDefinitionId();
            if (anchorId == null) {
                continue;
            }
            ActivityExecutionLocation existed = bestMap.get(anchorId);
            if (existed == null || this.isHigherPriority(rule, existed)) {
                bestMap.put(anchorId, rule);
            }
        }
        Map<Long, ExecutionRuleBatchHitQueryModel> result = new HashMap<>();
        for (Map.Entry<Long, ActivityExecutionLocation> entry : bestMap.entrySet()) {
            ExecutionRuleBatchHitQueryModel model = new ExecutionRuleBatchHitQueryModel();
            model.setAnchorId(entry.getKey());
            model.setRuleId(entry.getValue().getId());
            model.setExecutionOrgId(entry.getValue().getExecutionOrgId());
            model.setExecutionLocationId(entry.getValue().getExecutionLocationId());
            model.setPriority(entry.getValue().getPriority());
            result.put(entry.getKey(), model);
        }
        return result;
    }

    /**
     * 判断规则日时间窗是否覆盖查询时刻（闭区间）。
     *
     * @param rule   规则
     * @param atTime 查询时刻
     * @return 是否命中
     */
    private boolean hitAt(ActivityExecutionLocation rule, LocalTime atTime) {
        LocalTime start;
        if (rule.getStartTime() == null) {
            start = LocalTime.MIN;
        } else {
            start = rule.getStartTime();
        }
        LocalTime end;
        if (rule.getEndTime() == null) {
            end = LocalTime.MAX;
        } else {
            end = rule.getEndTime();
        }
        return !atTime.isBefore(start) && !atTime.isAfter(end);
    }

    /**
     * 判断左规则优先级是否高于右规则；优先级相等时用规则 id 较小者稳定决胜。
     *
     * @param left  左规则
     * @param right 右规则
     * @return {@code true} 表示左规则更优先
     */
    private boolean isHigherPriority(ActivityExecutionLocation left, ActivityExecutionLocation right) {
        Integer leftPriority = left.getPriority();
        Integer rightPriority = right.getPriority();
        int leftValue;
        if (leftPriority == null) {
            leftValue = Integer.MAX_VALUE;
        } else {
            leftValue = leftPriority;
        }
        int rightValue;
        if (rightPriority == null) {
            rightValue = Integer.MAX_VALUE;
        } else {
            rightValue = rightPriority;
        }
        if (leftValue < rightValue) {
            return true;
        }
        if (leftValue > rightValue) {
            return false;
        }
        return left.getId() < right.getId();
    }
}
