/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityexecutionlocation.service;

import com.zxplat.domain.custom.activityexecutionlocation.entity.ActivityExecutionLocation;
import com.zxplat.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleBatchHitQueryModel;
import com.zxplat.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleHitQueryModel;
import com.zxplat.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.zxplat.domain.custom.activityexecutionlocation.repository.ActivityExecutionLocationRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行科室规则读侧查询服务。
 *
 * <p>职责：按开立科室 + 诊疗定义（及可选业务类型）解析「在什么时间由哪个科室/地点执行」的规则列表与单条命中。</p>
 *
 * <p>边界：只读；开立科室或诊疗定义缺失时抛 {@link BusinessException}；无规则覆盖查询时刻时返回 {@code null} 或空列表由调用方处理。</p>
 *
 * <p>聚合边界：仅本聚合 {@link ActivityExecutionLocationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityExecutionLocationQueryService {

    private final ActivityExecutionLocationRepository repository;

    /**
     * 按开立科室 + 诊疗定义解析可命中的执行规则：单品 + 匹配业务类型层 + 同大类默认。
     *
     * <p>医生站开立诊疗时消费。未维护业务类型层时行为与仅「单品 + 大类默认」一致。
     * 其它业务类型（如开 DR 时配的 CT 室）不进入候选，避免串科室。</p>
     *
     * @param organizationId 开立科室标识（就诊科室）
     * @param activityDefinitionId 诊疗定义标识
     * @param activityCategoryCode 活动类别编码；为空时仅返回项目级规则
     * @param activityTypeCode 业务类型码（AD {@code type_enum}）；可空，空则不叠加类型层
     * @return 合并去重后的规则列表（排序：单品优先于类型层优先于大类，再按优先级）
     * @throws BusinessException 开立科室或诊疗定义标识为空
     */
    public List<ExecutionRuleListQueryModel> listExecutionRulesForActivityResolution(
        Long organizationId,
        Long activityDefinitionId,
        String activityCategoryCode,
        String activityTypeCode
    ) {
        // Gate：开立科室为空则无法按就诊科室过滤规则
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        // Gate：诊疗定义为空则无法定位单品层与上层回退上下文
        if (activityDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识不能为空");
        }
        Map<Long, ActivityExecutionLocation> uniqueRules = new HashMap<>();
        // Loop：先收项目级规则（最细粒度，覆盖类型层与大类）
        for (ActivityExecutionLocation rule :
            repository.findAllByOrganizationIdAndActivityDefinitionId(organizationId, activityDefinitionId)) {
            // 无主键的脏行不参与去重映射
            if (rule.getId() != null) {
                uniqueRules.put(rule.getId(), rule);
            }
        }
        String category = trimToNull(activityCategoryCode);
        // 无大类时无法叠加类型层/大类默认，仅返回单品规则。
        if (category != null) {
            String expectedType = trimToNull(activityTypeCode);
            // Loop：未绑单品行中筛出「匹配类型层 + 大类默认」，其它类型码丢弃防串科室
            for (ActivityExecutionLocation rule :
                repository.findAllByOrganizationIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
                    organizationId, category)) {
                // 无主键的脏行跳过
                if (rule.getId() == null) {
                    continue;
                }
                // 仅纳入与当前项目业务类型匹配的类型层，以及大类默认
                if (this.matchesUnresolvedDefinitionLayer(rule, expectedType)) {
                    uniqueRules.put(rule.getId(), rule);
                }
            }
        }
        List<ActivityExecutionLocation> rules = new ArrayList<>(uniqueRules.values());
        this.sortRulesForDisplay(rules);
        List<ExecutionRuleListQueryModel> result = new ArrayList<>();
        // Loop：实体投影为列表读模型
        for (ActivityExecutionLocation rule : rules) {
            result.add(this.toListModel(rule));
        }
        return result;
    }

    /**
     * 按开立科室查询规则列表（可按活动类别过滤）；WH-EXE001 配置页消费。
     *
     * @param organizationId 开立科室标识
     * @param activityCategoryCode 活动类别编码（可空）
     * @return 规则列表
     * @throws BusinessException 开立科室标识为空
     */
    public List<ExecutionRuleListQueryModel> listExecutionRulesByOrganization(
        Long organizationId,
        String activityCategoryCode
    ) {
        if (organizationId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
                "开立科室标识不能为空");
        }
        List<ActivityExecutionLocation> rules;
        String category = trimToNull(activityCategoryCode);
        // 未传大类时拉该开立科室下全部有效规则（含类型层与单品）
        if (category == null) {
            rules = repository.findAllByOrganizationId(organizationId);
        } else {
            // 传了大类则仅展示该大类下配置，便于配置页筛选
            rules = repository.findAllByOrganizationIdAndActivityCategoryCode(organizationId, category);
        }
        this.sortRulesForDisplay(rules);
        List<ExecutionRuleListQueryModel> result = new ArrayList<>();
        // Loop：投影为配置页列表读模型
        for (ActivityExecutionLocation rule : rules) {
            result.add(this.toListModel(rule));
        }
        return result;
    }

    /**
     * 按开立科室 + 诊疗定义与时刻解析单条最优先命中规则。
     *
     * <p>匹配顺序：单品 → 匹配业务类型层 → 大类默认。未维护中间层时与仅大类回退一致。</p>
     *
     * @param organizationId 开立科室标识
     * @param activityDefinitionId 诊疗定义标识
     * @param activityCategoryCode 活动类别编码；可空
     * @param activityTypeCode 业务类型码；可空
     * @param atTime 评核时刻；{@code null} 时使用当前时刻
     * @return 命中规则；无命中时 {@code null}
     * @throws BusinessException 开立科室或诊疗定义标识为空
     */
    public ExecutionRuleHitQueryModel findByActivityAndTime(
        Long organizationId,
        Long activityDefinitionId,
        String activityCategoryCode,
        String activityTypeCode,
        LocalTime atTime
    ) {
        // Gate：开立科室为空则无法解析执行目的地
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        // Gate：诊疗定义为空则无法按锚点命中
        if (activityDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识不能为空");
        }
        // 未传评核时刻时按当前时钟，保证时段窗可判定
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        List<ActivityExecutionLocation> rules =
            repository.findAllByOrganizationIdAndActivityDefinitionId(organizationId, activityDefinitionId);
        ActivityExecutionLocation hit = this.findTopHit(rules, atTime);
        String category = trimToNull(activityCategoryCode);
        // 单品未命中且有大类时，再走类型层 → 大类默认回退
        if (hit == null && category != null) {
            List<ActivityExecutionLocation> definitionNullRows =
                repository.findAllByOrganizationIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
                    organizationId, category);
            String expectedType = trimToNull(activityTypeCode);
            // 有业务类型时先吃类型层（如检查+DR→影像科），未配本层再落大类默认。
            if (expectedType != null) {
                hit = this.findTopHit(this.filterTypeLayer(definitionNullRows, expectedType), atTime);
            }
            // 类型层未命中（或项目无业务类型）时回落大类默认
            if (hit == null) {
                hit = this.findTopHit(this.filterCategoryDefaultLayer(definitionNullRows), atTime);
            }
        }
        // 三层皆无命中时由调用方决定是否回落开立科室
        if (hit == null) {
            return null;
        }
        return this.toHitModel(hit, activityDefinitionId);
    }

    /**
     * 批量按开立科室 + 诊疗定义与时刻解析各锚点的最优先命中规则（仅项目级）。
     *
     * @param organizationId 开立科室标识
     * @param activityDefinitionIds 诊疗定义标识集合
     * @param atTime 评核时刻；{@code null} 时使用当前时刻
     * @return 锚点到批量命中模型映射
     * @throws BusinessException 开立科室为空或集合为空
     */
    public Map<Long, ExecutionRuleBatchHitQueryModel> findForBatchByActivityAndTime(
        Long organizationId,
        List<Long> activityDefinitionIds,
        LocalTime atTime
    ) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        if (activityDefinitionIds == null || activityDefinitionIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义标识集合不能为空");
        }
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        List<ActivityExecutionLocation> rules =
            repository.findAllByOrganizationIdAndActivityDefinitionIdIn(organizationId, activityDefinitionIds);
        return this.groupTopHitsByActivityDefinition(rules, atTime);
    }

    /**
     * 未绑单品行是否纳入医生站候选：大类默认始终纳入；类型层仅纳入与当前项目业务类型相同的行。
     *
     * @param rule 待判定规则
     * @param expectedType 当前诊疗的业务类型码；空则不纳入任何类型层
     * @return 应进入候选时为 true
     */
    private boolean matchesUnresolvedDefinitionLayer(ActivityExecutionLocation rule, String expectedType) {
        // 单品行已由项目级查询纳入，这里只处理未绑单品层。
        if (rule.getActivityDefinitionId() != null) {
            return false;
        }
        String rowType = trimToNull(rule.getActivityTypeCode());
        // 业务类型为空即大类默认，始终作为回落候选。
        if (rowType == null) {
            return true;
        }
        // 项目未带业务类型时不吃类型层，保持「未配这一级跟以前一样」。
        if (expectedType == null) {
            return false;
        }
        return expectedType.equals(rowType);
    }

    /**
     * 筛出业务类型层规则（未绑单品且类型码等于入参）。
     *
     * @param rules 未绑单品候选
     * @param activityTypeCode 期望业务类型码
     * @return 类型层子集
     */
    private List<ActivityExecutionLocation> filterTypeLayer(
        List<ActivityExecutionLocation> rules,
        String activityTypeCode
    ) {
        List<ActivityExecutionLocation> result = new ArrayList<>();
        for (ActivityExecutionLocation rule : rules) {
            // 单品行不属于类型层。
            if (rule.getActivityDefinitionId() != null) {
                continue;
            }
            String rowType = trimToNull(rule.getActivityTypeCode());
            if (activityTypeCode.equals(rowType)) {
                result.add(rule);
            }
        }
        return result;
    }

    /**
     * 筛出大类默认规则（未绑单品且业务类型为空）。
     *
     * @param rules 未绑单品候选
     * @return 大类默认子集
     */
    private List<ActivityExecutionLocation> filterCategoryDefaultLayer(List<ActivityExecutionLocation> rules) {
        List<ActivityExecutionLocation> result = new ArrayList<>();
        for (ActivityExecutionLocation rule : rules) {
            // 单品行不属于大类默认。
            if (rule.getActivityDefinitionId() != null) {
                continue;
            }
            // 类型层有码，不能当作大类默认回落。
            if (trimToNull(rule.getActivityTypeCode()) == null) {
                result.add(rule);
            }
        }
        return result;
    }

    /**
     * 锚点粒度：单品最细，其次业务类型，再次大类默认。数值越小越优先展示/命中并列时靠前。
     *
     * @param rule 规则
     * @return 层级序
     */
    private int resolveAnchorLayerRank(ActivityExecutionLocation rule) {
        // 绑了单品则粒度最细，下拉默认应压过类型层与大类。
        if (rule.getActivityDefinitionId() != null) {
            return 0;
        }
        // 未绑单品但配了业务类型，优先于大类默认。
        if (trimToNull(rule.getActivityTypeCode()) != null) {
            return 1;
        }
        return 2;
    }

    /**
     * 列表展示排序：先按锚点粒度（单品 → 类型 → 大类），再优先级升序，其次 display_order，再 id。
     *
     * @param rules 待排序规则
     */
    private void sortRulesForDisplay(List<ActivityExecutionLocation> rules) {
        rules.sort(Comparator
            .comparingInt(this::resolveAnchorLayerRank)
            .thenComparing(ActivityExecutionLocation::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
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
        model.setOrganizationId(rule.getOrganizationId());
        model.setActivityDefinitionId(rule.getActivityDefinitionId());
        model.setActivityCategoryCode(rule.getActivityCategoryCode());
        model.setActivityTypeCode(rule.getActivityTypeCode());
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
     * @param rule 规则实体
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
     * @param rules 候选规则
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
            // 同刻多规则并存时取优先级更高者（数值小优先）。
            if (this.isHigherPriority(rule, best)) {
                best = rule;
            }
        }
        return best;
    }

    /**
     * 批量按诊疗定义分组选取命中规则（每个锚点保留一条最优规则）。
     *
     * @param rules 候选规则
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
     * @param rule 规则
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
     * @param left 左规则
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
