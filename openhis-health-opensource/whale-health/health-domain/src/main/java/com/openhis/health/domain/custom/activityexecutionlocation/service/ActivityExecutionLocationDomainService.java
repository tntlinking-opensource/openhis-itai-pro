/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.activityexecutionlocation.command.CreateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.DeleteExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.UpdateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.entity.ActivityExecutionLocation;
import com.openhis.health.domain.custom.activityexecutionlocation.repository.ActivityExecutionLocationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.activityexecutionlocation.ActivityExecutionLocationEditedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 执行科室规则（{@code cus_activity_execution_location}）聚合写侧领域服务。
 *
 * <p>职责：维护活动类别/诊疗定义锚点下的执行科室与时段规则；批量新增时预取候选集校验闭区间不重叠（BR-ACT-EXEC-001）。</p>
 * <p>不变量：同锚点时段不可重叠；软删后不再参与匹配；持久化先于 {@link ActivityExecutionLocationEditedEvent}。</p>
 * <p>仅注入本聚合 {@link ActivityExecutionLocationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityExecutionLocationDomainService {

    private final ActivityExecutionLocationRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新增执行规则（支持单条和批量）。
     *
     * @param command 新增命令
     * @return 新增主键列表
     * @throws BusinessException 参数非法或时段重叠冲突时
     */
    public List<Long> createExecutionRule(CreateExecutionRuleCommand command) {
        // 前置校验：活动类别、执行科室与明细非空
        if (command.getActivityCategoryCode() == null || command.getActivityCategoryCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "活动类别编码不能为空");
        }
        if (command.getExecutionOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室标识不能为空");
        }
        List<CreateExecutionRuleCommand.CreateExecutionRuleItemCommand> items = command.resolveRules();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "新增规则明细不能为空");
        }

        List<ActivityExecutionLocation> toSave = new ArrayList<>();
        Map<AnchorGroupKey, List<ActivityExecutionLocation>> newRulesByAnchor = new HashMap<>();
        // Loop：遍历命令项，逐条组装聚合根并进行字段级不变式校验。
        for (CreateExecutionRuleCommand.CreateExecutionRuleItemCommand item : items) {
            ActivityExecutionLocation rule = new ActivityExecutionLocation();
            rule.setActivityDefinitionId(item.getActivityDefinitionId());
            rule.setActivityCategoryCode(command.getActivityCategoryCode());
            rule.setExecutionOrgId(command.getExecutionOrgId());
            rule.setExecutionLocationId(item.getExecutionLocationId());
            rule.setStartTime(item.getStartTime());
            rule.setEndTime(item.getEndTime());
            rule.setPriority(item.getPriority());
            rule.setDisplayOrder(item.getDisplayOrder());
            rule.setRemark(item.getRemark());
            rule.assertRequiredFields();
            rule.assertAnchorPresent();

            // Pre：先收集到批量待处理集合，重叠校验统一批量预取候选集后执行，避免 N+1 数据库访问。
            this.appendToNewRulesMap(newRulesByAnchor, rule);
            toSave.add(rule);
        }

        // Pre：对同锚点候选集执行时段重叠校验，防止配置冲突（批量预取 + 内存校验）。
        Map<AnchorGroupKey, List<ActivityExecutionLocation>> existedByAnchor = this.loadCandidatesForBatchCreate(newRulesByAnchor.keySet());
        this.assertNoTimeOverlapForBatchCreate(existedByAnchor, newRulesByAnchor);

        // 持久化
        List<ActivityExecutionLocation> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        // 发布事件：逐条通知规则变更
        for (ActivityExecutionLocation rule : saved) {
            ids.add(rule.getId());
            domainEventPublisher.publish(new ActivityExecutionLocationEditedEvent(
                rule.getId(),
                CustomConstants.OperationType.CREATE,
                rule.getActivityCategoryCode(),
                rule.getExecutionOrgId(),
                command.getOperatorId()));
        }
        return ids;
    }

    /**
     * 更新执行规则。
     *
     * @param command 更新命令
     * @throws BusinessException 规则不存在或时段重叠冲突时
     */
    public void updateExecutionRule(UpdateExecutionRuleCommand command) {
        // 加载/组装：加载目标规则并合并命令字段
        ActivityExecutionLocation rule = repository.findById(command.getId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_NOT_FOUND, "执行科室规则不存在"));

        // 合并命令字段到聚合根并执行必填与锚点校验。
        // 诊疗定义可空（大类默认规则），更新时须允许显式清空项目锚点。
        rule.setActivityDefinitionId(command.getActivityDefinitionId());
        // 命令传入活动类别编码时覆盖头表类别。
        if (command.getActivityCategoryCode() != null) {
            rule.setActivityCategoryCode(command.getActivityCategoryCode());
        }
        // 命令传入执行科室标识时覆盖头表科室。
        if (command.getExecutionOrgId() != null) {
            rule.setExecutionOrgId(command.getExecutionOrgId());
        }
        // 命令传入执行地点标识时覆盖明细地点。
        if (command.getExecutionLocationId() != null) {
            rule.setExecutionLocationId(command.getExecutionLocationId());
        }
        // 命令传入时段开始时间时覆盖规则起始时刻。
        if (command.getStartTime() != null) {
            rule.setStartTime(command.getStartTime());
        }
        // 命令传入时段结束时间时覆盖规则截止时刻。
        if (command.getEndTime() != null) {
            rule.setEndTime(command.getEndTime());
        }
        // 命令传入优先级时覆盖匹配排序权重。
        if (command.getPriority() != null) {
            rule.setPriority(command.getPriority());
        }
        // 命令传入展示排序时覆盖列表展示顺序。
        if (command.getDisplayOrder() != null) {
            rule.setDisplayOrder(command.getDisplayOrder());
        }
        // 命令传入备注时覆盖规则说明。
        if (command.getRemark() != null) {
            rule.setRemark(command.getRemark());
        }
        rule.assertRequiredFields();
        rule.assertAnchorPresent();

        // 前置校验：更新时排除自身后校验时段重叠不变式
        this.assertNoTimeOverlapOnUpdate(rule);

        // 持久化
        repository.save(rule);
        // 发布事件：规则已更新
        domainEventPublisher.publish(new ActivityExecutionLocationEditedEvent(
            rule.getId(),
            CustomConstants.OperationType.UPDATE,
            rule.getActivityCategoryCode(),
            rule.getExecutionOrgId(),
            command.getOperatorId()));
    }

    /**
     * 软删执行规则。
     *
     * @param command 删除命令
     * @throws BusinessException 规则不存在时
     */
    public void deleteExecutionRule(DeleteExecutionRuleCommand command) {
        // 加载/组装：删除动作仅对可见规则生效
        ActivityExecutionLocation rule = repository.findById(command.getId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_NOT_FOUND, "执行科室规则不存在"));
        rule.markDeleted(command.getOperatorId());

        // 持久化
        repository.save(rule);
        // 发布事件：规则已软删
        domainEventPublisher.publish(new ActivityExecutionLocationEditedEvent(
            rule.getId(),
            CustomConstants.OperationType.DELETE,
            rule.getActivityCategoryCode(),
            rule.getExecutionOrgId(),
            command.getOperatorId()));
    }

    /**
     * 校验更新场景时段是否重叠（排除待更新规则自身）。
     *
     * <p>遍历同活动类别、同科室的已有规则，若存在与 candidate 时段重叠且 ID 不同的规则，则抛出异常。</p>
     *
     * @param candidate 待更新规则，其 id 字段用于排除自身
     * @throws BusinessException 时段与已有规则重叠
     */
    private void assertNoTimeOverlapOnUpdate(ActivityExecutionLocation candidate) {
        List<ActivityExecutionLocation> existed = this.loadCandidates(candidate);
        for (ActivityExecutionLocation item : existed) {
            // 更新校验时跳过自身，避免与旧时段误判重叠。
            if (Objects.equals(item.getId(), candidate.getId())) {
                continue;
            }
            // 与同锚点存量规则时段闭区间重叠则拒绝更新。
            if (this.isOverlapped(item.getStartTime(), item.getEndTime(), candidate.getStartTime(), candidate.getEndTime())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_RULE_CONFLICT, "执行科室规则时段存在重叠");
            }
        }
    }

    /**
     * 按锚点加载候选规则集合。
     *
     * <p>按诊疗定义锚点查询同科室同类别下的已有规则。</p>
     *
     * @param candidate 待处理规则
     * @return 候选集合（去重后的已有规则列表）
     */
    private List<ActivityExecutionLocation> loadCandidates(ActivityExecutionLocation candidate) {
        if (candidate.getActivityDefinitionId() == null) {
            return repository.findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
                candidate.getExecutionOrgId(),
                candidate.getActivityCategoryCode()
            );
        }
        return repository.findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionId(
            candidate.getExecutionOrgId(),
            candidate.getActivityCategoryCode(),
            candidate.getActivityDefinitionId()
        );
    }

    /**
     * 将新增规则加入“按锚点分组”的批量容器中。
     *
     * <p>每条规则按诊疗定义锚点分组参与重叠校验。</p>
     *
     * @param newRulesByAnchor 新增规则分组容器
     * @param rule 待新增规则
     */
    private void appendToNewRulesMap(Map<AnchorGroupKey, List<ActivityExecutionLocation>> newRulesByAnchor, ActivityExecutionLocation rule) {
        // 分组前校验执行科室与活动类别必填，否则无法定位重叠校验锚点。
        if (rule.getExecutionOrgId() == null || rule.getActivityCategoryCode() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室标识与活动类别编码不能为空");
        }
        AnchorGroupKey key;
        if (rule.getActivityDefinitionId() == null) {
            // 未配具体项目时，按「执行科室 + 活动类别」分组做时段重叠校验。
            key = AnchorGroupKey.forCategory(rule.getExecutionOrgId(), rule.getActivityCategoryCode());
        } else {
            key = AnchorGroupKey.forActivity(
                rule.getExecutionOrgId(), rule.getActivityCategoryCode(), rule.getActivityDefinitionId());
        }
        this.appendRule(newRulesByAnchor, key, rule);
    }

    /**
     * 按 key 追加到 list。
     *
     * <p>若分组容器中不存在该 key，则创建新列表并放入容器。</p>
     *
     * @param newRulesByAnchor 分组容器
     * @param key 分组 key
     * @param rule 规则
     */
    private void appendRule(Map<AnchorGroupKey, List<ActivityExecutionLocation>> newRulesByAnchor, AnchorGroupKey key, ActivityExecutionLocation rule) {
        List<ActivityExecutionLocation> list = newRulesByAnchor.get(key);
        // 分组 key 首次出现时初始化列表容器。
        if (list == null) {
            list = new ArrayList<>();
            newRulesByAnchor.put(key, list);
        }
        list.add(rule);
    }

    /**
     * 批量加载“新增规则所涉及锚点”的候选存量规则，用于一次性完成时段重叠校验，避免 N+1。
     *
     * @param keys 锚点分组 key 集合
     * @return 存量规则分组结果
     */
    private Map<AnchorGroupKey, List<ActivityExecutionLocation>> loadCandidatesForBatchCreate(Collection<AnchorGroupKey> keys) {
        Map<AnchorGroupKey, List<ActivityExecutionLocation>> result = new HashMap<>();
        Map<OrgCategoryKey, Set<Long>> activityIdsByOrgCategory = new HashMap<>();
        Set<OrgCategoryKey> categoryOnlyOrgCategories = new HashSet<>();

        // 先按（执行科室 + 活动类别）分组，收集诊疗锚点集合，后续用 IN 查询一次性预取。
        for (AnchorGroupKey key : keys) {
            OrgCategoryKey orgCategoryKey = new OrgCategoryKey(key.getExecutionOrgId(), key.getActivityCategoryCode());
            if (key.getActivityDefinitionId() == null) {
                categoryOnlyOrgCategories.add(orgCategoryKey);
                continue;
            }
            Set<Long> ids = activityIdsByOrgCategory.get(orgCategoryKey);
            if (ids == null) {
                ids = new HashSet<>();
                activityIdsByOrgCategory.put(orgCategoryKey, ids);
            }
            ids.add(key.getActivityDefinitionId());
        }

        // 大类默认规则：按（执行科室 + 活动类别）预取 activity_definition_id 为空的存量行。
        for (OrgCategoryKey orgCategoryKey : categoryOnlyOrgCategories) {
            List<ActivityExecutionLocation> existed =
                repository.findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
                    orgCategoryKey.getExecutionOrgId(),
                    orgCategoryKey.getActivityCategoryCode()
                );
            AnchorGroupKey anchorKey = AnchorGroupKey.forCategory(
                orgCategoryKey.getExecutionOrgId(),
                orgCategoryKey.getActivityCategoryCode());
            for (ActivityExecutionLocation row : existed) {
                this.appendRule(result, anchorKey, row);
            }
        }

        // 诊疗定义锚点：按（执行科室 + 活动类别）批量 IN 预取，再按 anchorId 回填到分组 map。
        for (Map.Entry<OrgCategoryKey, Set<Long>> entry : activityIdsByOrgCategory.entrySet()) {
            List<ActivityExecutionLocation> existed = repository.findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionIdIn(
                entry.getKey().getExecutionOrgId(),
                entry.getKey().getActivityCategoryCode(),
                entry.getValue()
            );
            for (ActivityExecutionLocation row : existed) {
                if (row.getActivityDefinitionId() == null) {
                    continue;
                }
                AnchorGroupKey anchorKey = AnchorGroupKey.forActivity(
                    entry.getKey().getExecutionOrgId(),
                    entry.getKey().getActivityCategoryCode(),
                    row.getActivityDefinitionId());
                this.appendRule(result, anchorKey, row);
            }
        }

        // 统一去重（同一存量规则可能因数据口径被重复回填）。
        for (Map.Entry<AnchorGroupKey, List<ActivityExecutionLocation>> entry : result.entrySet()) {
            Map<Long, ActivityExecutionLocation> unique = new HashMap<>();
            for (ActivityExecutionLocation row : entry.getValue()) {
                unique.put(row.getId(), row);
            }
            entry.setValue(new ArrayList<>(unique.values()));
        }
        return result;
    }

    /**
     * 批量新增场景：对每个锚点分组执行“闭区间时段不重叠”校验。
     *
     * <p>校验包含两部分：存量规则之间（理论上应已满足）与“存量 vs 新增”“新增 vs 新增”。</p>
     *
     * @param existedByAnchor 存量规则分组
     * @param newRulesByAnchor 新增规则分组
     */
    private void assertNoTimeOverlapForBatchCreate(
        Map<AnchorGroupKey, List<ActivityExecutionLocation>> existedByAnchor,
        Map<AnchorGroupKey, List<ActivityExecutionLocation>> newRulesByAnchor
    ) {
        for (Map.Entry<AnchorGroupKey, List<ActivityExecutionLocation>> entry : newRulesByAnchor.entrySet()) {
            List<ActivityExecutionLocation> combined = new ArrayList<>();
            List<ActivityExecutionLocation> existed = existedByAnchor.get(entry.getKey());
            // 同锚点存在存量规则时并入待校验集合，校验存量与新增之间不重叠。
            if (existed != null && !existed.isEmpty()) {
                combined.addAll(existed);
            }
            combined.addAll(entry.getValue());
            this.assertNoTimeOverlapInSameAnchorGroup(combined);
        }
    }

    /**
     * 同一锚点分组内，校验闭区间时段不重叠。
     *
     * @param rules 同一锚点下的候选规则（包含存量与新增）
     */
    private void assertNoTimeOverlapInSameAnchorGroup(List<ActivityExecutionLocation> rules) {
        // 单条或空集合无需做时段重叠检测。
        if (rules == null || rules.size() <= 1) {
            return;
        }
        // 按时段开始时间 → 结束时间 → ID（新建中 ID 为 null 时排最前）排序，用于后续时段重叠检测。
        rules.sort(Comparator
            .comparing((ActivityExecutionLocation r) -> this.normalizeStart(r.getStartTime()))
            .thenComparing(r -> this.normalizeEnd(r.getEndTime()))
            .thenComparing(r -> {
                // ID 为 null 表示待新增规则，以 Long.MIN_VALUE 排最前，保证与已有规则的时段比对顺序稳定。
                if (r.getId() == null) {
                    return Long.MIN_VALUE;
                }
                return r.getId();
            }));

        LocalTime prevEnd = this.normalizeEnd(rules.get(0).getEndTime());
        for (int i = 1; i < rules.size(); i++) {
            LocalTime currStart = this.normalizeStart(rules.get(i).getStartTime());
            LocalTime currEnd = this.normalizeEnd(rules.get(i).getEndTime());

            // 闭区间重叠：currStart <= prevEnd 即视为冲突（BR-ACT-EXEC-001）。
            if (!prevEnd.isBefore(currStart)) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_RULE_CONFLICT, "执行科室规则时段存在重叠");
            }

            // 保持 prevEnd 为当前扫描段的最大 end，覆盖包含型区间。
            if (currEnd.isAfter(prevEnd)) {
                prevEnd = currEnd;
            }
        }
    }

    /**
     * 规范化开始时间：空表示全天，按最小值处理。
     *
     * @param startTime 开始时间
     * @return 规范化开始时间
     */
    private LocalTime normalizeStart(LocalTime startTime) {
        // 开始时间为空表示全天起始，按最小时刻参与重叠比较。
        if (startTime == null) {
            return LocalTime.MIN;
        }
        return startTime;
    }

    /**
     * 规范化结束时间：空表示全天，按最大值处理。
     *
     * @param endTime 结束时间
     * @return 规范化结束时间
     */
    private LocalTime normalizeEnd(LocalTime endTime) {
        // 结束时间为空表示全天截止，按最大时刻参与重叠比较。
        if (endTime == null) {
            return LocalTime.MAX;
        }
        return endTime;
    }

    /**
     * （执行科室 + 活动类别）维度的分组 key。
     */
    private static class OrgCategoryKey {
        private final Long executionOrgId;
        private final String activityCategoryCode;

        /**
         * 构造「执行科室 + 活动类别」分组键。
         */
        private OrgCategoryKey(Long executionOrgId, String activityCategoryCode) {
            this.executionOrgId = executionOrgId;
            this.activityCategoryCode = activityCategoryCode;
        }

        /**
         * @return 执行科室主键
         */
        private Long getExecutionOrgId() {
            return executionOrgId;
        }

        /**
         * @return 活动类别 code
         */
        private String getActivityCategoryCode() {
            return activityCategoryCode;
        }

        @Override
        public boolean equals(Object o) {
            // 同一引用直接判定相等。
            if (this == o) {
                return true;
            }
            // 类型不匹配则分组键不等。
            if (!(o instanceof OrgCategoryKey)) {
                return false;
            }
            OrgCategoryKey that = (OrgCategoryKey) o;
            return Objects.equals(executionOrgId, that.executionOrgId)
                && Objects.equals(activityCategoryCode, that.activityCategoryCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executionOrgId, activityCategoryCode);
        }
    }

    /**
     * （执行科室 + 活动类别 + 诊疗定义）维度的分组 key。
     */
    private static class AnchorGroupKey {
        private final Long executionOrgId;
        private final String activityCategoryCode;
        private final Long activityDefinitionId;

        private AnchorGroupKey(Long executionOrgId, String activityCategoryCode, Long activityDefinitionId) {
            this.executionOrgId = executionOrgId;
            this.activityCategoryCode = activityCategoryCode;
            this.activityDefinitionId = activityDefinitionId;
        }

        /**
         * 构造「执行科室 + 活动类别 + 诊疗定义」锚点分组键（定义级规则）。
         *
         * @param executionOrgId        执行科室
         * @param activityCategoryCode  活动类别 code
         * @param activityDefinitionId  诊疗定义主键
         * @return 锚点分组键
         */
        private static AnchorGroupKey forActivity(
            Long executionOrgId,
            String activityCategoryCode,
            Long activityDefinitionId
        ) {
            return new AnchorGroupKey(executionOrgId, activityCategoryCode, activityDefinitionId);
        }

        /**
         * 构造「执行科室 + 活动类别」锚点分组键（类别级规则，无诊疗定义）。
         *
         * @param executionOrgId       执行科室
         * @param activityCategoryCode 活动类别 code
         * @return 锚点分组键（activityDefinitionId 为 null）
         */
        private static AnchorGroupKey forCategory(
            Long executionOrgId,
            String activityCategoryCode
        ) {
            return new AnchorGroupKey(executionOrgId, activityCategoryCode, null);
        }

        /**
         * @return 执行科室主键
         */
        private Long getExecutionOrgId() {
            return executionOrgId;
        }

        /**
         * @return 活动类别 code
         */
        private String getActivityCategoryCode() {
            return activityCategoryCode;
        }

        /**
         * @return 诊疗定义主键；类别级规则时为 null
         */
        private Long getActivityDefinitionId() {
            return activityDefinitionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AnchorGroupKey)) {
                return false;
            }
            AnchorGroupKey that = (AnchorGroupKey) o;
            return Objects.equals(executionOrgId, that.executionOrgId)
                && Objects.equals(activityCategoryCode, that.activityCategoryCode)
                && Objects.equals(activityDefinitionId, that.activityDefinitionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executionOrgId, activityCategoryCode, activityDefinitionId);
        }
    }

    /**
     * 判断两个闭区间时段是否重叠。
     *
     * @param leftStart 左侧开始
     * @param leftEnd 左侧结束
     * @param rightStart 右侧开始
     * @param rightEnd 右侧结束
     * @return 是否重叠
     */
    private boolean isOverlapped(LocalTime leftStart, LocalTime leftEnd, LocalTime rightStart, LocalTime rightEnd) {
        LocalTime normalizedLeftStart;
        // 左侧开始时间为空时按全天起始处理。
        if (leftStart == null) {
            normalizedLeftStart = LocalTime.MIN;
        } else {
            normalizedLeftStart = leftStart;
        }
        LocalTime normalizedLeftEnd;
        // 左侧结束时间为空时按全天截止处理。
        if (leftEnd == null) {
            normalizedLeftEnd = LocalTime.MAX;
        } else {
            normalizedLeftEnd = leftEnd;
        }
        LocalTime normalizedRightStart;
        // 右侧开始时间为空时按全天起始处理。
        if (rightStart == null) {
            normalizedRightStart = LocalTime.MIN;
        } else {
            normalizedRightStart = rightStart;
        }
        LocalTime normalizedRightEnd;
        // 右侧结束时间为空时按全天截止处理。
        if (rightEnd == null) {
            normalizedRightEnd = LocalTime.MAX;
        } else {
            normalizedRightEnd = rightEnd;
        }
        return !normalizedLeftEnd.isBefore(normalizedRightStart) && !normalizedRightEnd.isBefore(normalizedLeftStart);
    }
}
