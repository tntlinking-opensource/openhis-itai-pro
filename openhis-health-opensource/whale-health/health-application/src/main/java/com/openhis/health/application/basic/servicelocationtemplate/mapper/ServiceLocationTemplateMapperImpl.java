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

package com.openhis.health.application.basic.servicelocationtemplate.mapper;

import com.openhis.health.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateDto;
import com.openhis.health.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigItemRequest;
import com.openhis.health.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigRequest;
import com.openhis.health.domain.custom.servicelocationtemplate.command.SaveDistributionConfigCommand;
import com.openhis.health.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ServiceLocationTemplateMapper} 默认实现：逐字段搬运，不含业务校验。
 */
@Component
public class ServiceLocationTemplateMapperImpl implements ServiceLocationTemplateMapper {

    /**
     * 将规则读模型投影为展示 DTO。
     *
     * @param model 领域读模型；{@code null} 时返回 {@code null}
     * @return 展示 DTO
     */
    @Override
    public ServiceLocationTemplateDto toDto(ServiceLocationTemplateQueryModel model) {
        if (model == null) {
            return null;
        }
        ServiceLocationTemplateDto dto = new ServiceLocationTemplateDto();
        dto.setId(model.getId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setItemTable(model.getItemTable());
        dto.setDistributionCategoryCode(model.getDistributionCategoryCode());
        dto.setDefLocationId(model.getDefLocationId());
        dto.setItemDefinitionId(model.getItemDefinitionId());
        dto.setMethodCode(model.getMethodCode());
        dto.setStartTime(model.getStartTime());
        dto.setEndTime(model.getEndTime());
        dto.setPriority(model.getPriority());
        dto.setDisplayOrder(model.getDisplayOrder());
        dto.setRemark(model.getRemark());
        return dto;
    }

    /**
     * 将整科覆盖保存请求装配为领域命令。
     *
     * @param request 保存请求；调用方保证非空且已通过应用层 Gate
     * @param operatorId 当前操作人
     * @return 领域保存命令
     */
    @Override
    public SaveDistributionConfigCommand toSaveCommand(
        SaveServiceLocationTemplateConfigRequest request,
        Long operatorId
    ) {
        SaveDistributionConfigCommand command = new SaveDistributionConfigCommand();
        command.setOrganizationId(request.getOrganizationId());
        command.setOperatorId(operatorId);
        List<SaveDistributionConfigCommand.DistributionRuleItemCommand> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (SaveServiceLocationTemplateConfigItemRequest row : request.getItems()) {
                SaveDistributionConfigCommand.DistributionRuleItemCommand item = this.toSaveItem(row);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将单条配置行请求装配为领域命令子项。
     *
     * @param item 配置行；{@code null} 时返回 {@code null}
     * @return 领域子命令项
     */
    @Override
    public SaveDistributionConfigCommand.DistributionRuleItemCommand toSaveItem(
        SaveServiceLocationTemplateConfigItemRequest item
    ) {
        if (item == null) {
            return null;
        }
        SaveDistributionConfigCommand.DistributionRuleItemCommand row =
            new SaveDistributionConfigCommand.DistributionRuleItemCommand();
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
        return row;
    }
}
