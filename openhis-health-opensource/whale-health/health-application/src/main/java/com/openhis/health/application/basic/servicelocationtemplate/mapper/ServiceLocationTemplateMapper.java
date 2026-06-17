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

/**
 * 取药科室模板应用层与领域 Command/QueryModel 之间的映射契约；不承载业务流程判断。
 */
public interface ServiceLocationTemplateMapper {

    /**
     * 将规则读模型投影为展示 DTO。
     *
     * @param model 领域读模型；{@code null} 时返回 {@code null}
     * @return 展示 DTO
     */
    ServiceLocationTemplateDto toDto(ServiceLocationTemplateQueryModel model);

    /**
     * 将整科覆盖保存请求装配为领域命令。
     *
     * @param request 保存请求；调用方保证非空且已通过应用层 Gate
     * @param operatorId 当前操作人
     * @return 领域保存命令
     */
    SaveDistributionConfigCommand toSaveCommand(SaveServiceLocationTemplateConfigRequest request, Long operatorId);

    /**
     * 将单条配置行请求装配为领域命令子项。
     *
     * @param item 配置行；{@code null} 时返回 {@code null}
     * @return 领域子命令项
     */
    SaveDistributionConfigCommand.DistributionRuleItemCommand toSaveItem(
        SaveServiceLocationTemplateConfigItemRequest item);
}
