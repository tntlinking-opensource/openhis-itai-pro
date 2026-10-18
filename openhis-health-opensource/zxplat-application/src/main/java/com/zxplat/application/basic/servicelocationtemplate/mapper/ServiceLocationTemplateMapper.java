/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicelocationtemplate.mapper;

import com.zxplat.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateDto;
import com.zxplat.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigItemRequest;
import com.zxplat.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigRequest;
import com.zxplat.domain.custom.servicelocationtemplate.command.SaveDistributionConfigCommand;
import com.zxplat.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;

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
