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
 * 取药科室模板写侧领域服务。
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
        // 业务分支处理。
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
        // 业务分支处理。
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

        // 先落库后发事件，避免事务失败导致幽灵事件。
        domainEventPublisher.publish(new ServiceLocationTemplateEditedEvent(
            command.getOrganizationId(),
            command.getOrganizationId(),
            items.size()
        , command.getOperatorId()));
    }

    /**
     * 删除科室下全部规则。
     */
    public void deleteDistributionRulesByOrganization(DeleteDistributionRulesByOrganizationCommand command) {
        List<ServiceLocationTemplate> existed = repository.findAllByOrganizationId(command.getOrganizationId());
        for (ServiceLocationTemplate row : existed) {
            row.markDeleted(command.getOperatorId());
        }
        // 业务分支处理。
        if (!existed.isEmpty()) {
            repository.saveAll(existed);
        }
    }

    /**
     * 批量插入规则。
     */
    public void batchInsertDistributionRules(BatchInsertDistributionRulesCommand command) {
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
            // 业务分支处理。
            if (item.getPriority() == null) {
                continue;
            }
            priorities.add(item.getPriority());
        }
        priorities.sort(Integer::compareTo);
        for (int i = 0; i < priorities.size(); i++) {
            int expected = i + 1;
            // 业务分支处理。
            if (priorities.get(i) != expected) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "优先级必须从 1 开始连续分配");
            }
        }
    }
}

