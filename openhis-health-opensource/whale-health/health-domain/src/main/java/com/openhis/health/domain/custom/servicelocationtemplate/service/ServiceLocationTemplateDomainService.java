/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicelocationtemplate.service;

import com.openhis.health.domain.custom.servicelocationtemplate.command.BatchInsertDistributionRulesCommand;
import com.openhis.health.domain.custom.servicelocationtemplate.command.DeleteDistributionRulesByOrganizationCommand;
import com.openhis.health.domain.custom.servicelocationtemplate.command.SaveDistributionConfigCommand;
import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.openhis.health.domain.custom.servicelocationtemplate.repository.ServiceLocationTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.servicelocationtemplate.ServiceLocationTemplateEditedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 取药科室模板（{@code cus_service_location_template}）聚合写侧领域服务。
 *
 * <p>职责：按开立科室维护发放规则（整科覆盖保存、批量插入、按科软删）。</p>
 * <p>不变量：组合键不重复（BR-SLT-004）、优先级从 1 连续；持久化先于 {@link ServiceLocationTemplateEditedEvent}。</p>
 * <p>仅注入本聚合 {@link ServiceLocationTemplateRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceLocationTemplateDomainService {

    private final ServiceLocationTemplateRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 整科覆盖保存（单事务：先软删后插，避免中间态对外可见）。
     *
     * @param command 开立科室与规则行列表
     */
    public void saveDistributionConfig(SaveDistributionConfigCommand command) {
        // 前置校验：开立科室、组合键与优先级连续
        if (command.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        List<SaveDistributionConfigCommand.DistributionRuleItemCommand> items = command.resolveItems();
        this.assertNoDuplicateKey(command.getOrganizationId(), items);
        this.assertPriorityContinuous(items);

        // 整科覆盖：先软删后插；由应用层提供事务边界，避免中间态对外可见。
        List<ServiceLocationTemplate> existed = repository.findAllByOrganizationId(command.getOrganizationId());
        for (ServiceLocationTemplate row : existed) {
            row.markDeleted(command.getOperatorId());
        }
        // 存在存量规则时先批量软删，再插入新配置实现整科覆盖。
        if (!existed.isEmpty()) {
            repository.saveAll(existed);
        }

        List<ServiceLocationTemplate> toSave = new ArrayList<>();
        for (SaveDistributionConfigCommand.DistributionRuleItemCommand item : items) {
            ServiceLocationTemplate row = new ServiceLocationTemplate();
            row.setOrganizationId(command.getOrganizationId());
            row.setItemTable(item.getItemTable());
            row.setDistributionCategoryCode(item.getDistributionCategoryCode());
            row.setDefLocationId(item.getDefLocationId());
            row.setItemDefinitionId(item.getItemDefinitionId());
            row.setMethodCode(item.getMethodCode());
            row.setStartTime(item.getStartTime());
            row.setEndTime(item.getEndTime());
            row.setPriority(item.getPriority());
            row.setDisplayOrder(item.getDisplayOrder());
            row.setRemark(item.getRemark());
            row.assertRequiredFields();
            toSave.add(row);
        }
        repository.saveAll(toSave);

        // 发布事件：整科规则已覆盖保存
        domainEventPublisher.publish(new ServiceLocationTemplateEditedEvent(
            command.getOrganizationId(),
            command.getOrganizationId(),
            items.size()
        , command.getOperatorId()));
    }

    /**
     * 删除科室下全部规则。
     *
     * @param command 开立科室与操作人
     */
    public void deleteDistributionRulesByOrganization(DeleteDistributionRulesByOrganizationCommand command) {
        // 加载/组装：软删科室下全部存量规则
        List<ServiceLocationTemplate> existed = repository.findAllByOrganizationId(command.getOrganizationId());
        for (ServiceLocationTemplate row : existed) {
            row.markDeleted(command.getOperatorId());
        }
        // 存在存量规则时先批量软删，再插入新配置实现整科覆盖。
        if (!existed.isEmpty()) {
            repository.saveAll(existed);
        }
    }

    /**
     * 批量插入规则。
     *
     * @param command 开立科室与规则行列表
     */
    public void batchInsertDistributionRules(BatchInsertDistributionRulesCommand command) {
        // 加载/组装：逐行装配规则实体
        List<BatchInsertDistributionRulesCommand.DistributionRuleItemCommand> items = command.resolveItems();
        List<ServiceLocationTemplate> toSave = new ArrayList<>();
        for (BatchInsertDistributionRulesCommand.DistributionRuleItemCommand item : items) {
            ServiceLocationTemplate row = new ServiceLocationTemplate();
            row.setOrganizationId(command.getOrganizationId());
            row.setItemTable(item.getItemTable());
            row.setDistributionCategoryCode(item.getDistributionCategoryCode());
            row.setDefLocationId(item.getDefLocationId());
            row.setItemDefinitionId(item.getItemDefinitionId());
            row.setMethodCode(item.getMethodCode());
            row.setStartTime(item.getStartTime());
            row.setEndTime(item.getEndTime());
            row.setPriority(item.getPriority());
            row.setDisplayOrder(item.getDisplayOrder());
            row.setRemark(item.getRemark());
            row.assertRequiredFields();
            toSave.add(row);
        }
        // 持久化
        repository.saveAll(toSave);
    }

    /**
     * 校验「物品表 + 发放类别 + 具体项 + 用法 + 生效时段」组合键在本次提交中不重复（BR-SLT-004）。
     *
     * @param organizationId 开立科室/机构标识
     * @param items 待保存规则列表
     */
    private void assertNoDuplicateKey(Long organizationId, List<SaveDistributionConfigCommand.DistributionRuleItemCommand> items) {
        Set<String> keys = new HashSet<>();
        for (SaveDistributionConfigCommand.DistributionRuleItemCommand item : items) {
            String key = organizationId + "\u0001"
                + item.getItemTable() + "\u0001"
                + item.getDistributionCategoryCode() + "\u0001"
                + String.valueOf(item.getItemDefinitionId()) + "\u0001"
                + String.valueOf(item.getMethodCode()) + "\u0001"
                + this.timeWindowKey(item.getStartTime(), item.getEndTime());
            // 同一科室下五元组+时段完全相同视为重复配置，拒绝保存以免匹配歧义
            if (keys.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "规则配置键不可重复");
            }
            keys.add(key);
        }
    }

    /**
     * 将生效时段规范化为组合键片段；双空表示全天有效。
     *
     * @param startTime 生效开始
     * @param endTime 生效结束
     * @return 时段键
     */
    private String timeWindowKey(LocalTime startTime, LocalTime endTime) {
        String startPart;
        if (startTime == null) {
            startPart = "";
        } else {
            startPart = startTime.toString();
        }
        String endPart;
        if (endTime == null) {
            endPart = "";
        } else {
            endPart = endTime.toString();
        }
        return startPart + "\u0002" + endPart;
    }

    /**
     * 校验优先级从 1 开始连续分配。
     *
     * @param items 待保存规则列表
     */
    private void assertPriorityContinuous(List<SaveDistributionConfigCommand.DistributionRuleItemCommand> items) {
        List<Integer> priorities = new ArrayList<>();
        for (SaveDistributionConfigCommand.DistributionRuleItemCommand item : items) {
            // 未配置优先级的规则不参与连续性校验。
            if (item.getPriority() == null) {
                continue;
            }
            priorities.add(item.getPriority());
        }
        priorities.sort(Integer::compareTo);
        for (int i = 0; i < priorities.size(); i++) {
            int expected = i + 1;
            // 优先级必须从 1 连续递增，否则匹配顺序不确定。
            if (priorities.get(i) != expected) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "优先级必须从 1 开始连续分配");
            }
        }
    }
}
