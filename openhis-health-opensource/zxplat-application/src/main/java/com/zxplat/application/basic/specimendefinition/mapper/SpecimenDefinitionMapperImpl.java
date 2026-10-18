/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimendefinition.mapper;

import com.zxplat.application.basic.specimendefinition.dto.SpecimenDefinitionDto;
import com.zxplat.application.basic.specimendefinition.request.CreateSpecimenDefinitionRequest;
import com.zxplat.application.basic.specimendefinition.request.UpdateSpecimenDefinitionRequest;
import com.zxplat.domain.administration.specimendefinition.command.CreateSpecimenDefinitionCommand;
import com.zxplat.domain.administration.specimendefinition.command.UpdateSpecimenDefinitionCommand;
import com.zxplat.domain.administration.specimendefinition.queries.model.SpecimenDefinitionDetailQueryModel;
import com.zxplat.domain.administration.specimendefinition.queries.model.SpecimenDefinitionPageItemQueryModel;
import com.zxplat.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.SpecimenType;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SpecimenDefinitionMapper} 默认实现：列表/详情/检索三路透射，枚举展示文案由术语服务解析。
 */
@Component
@RequiredArgsConstructor
public class SpecimenDefinitionMapperImpl implements SpecimenDefinitionMapper {

    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 分页列表读模型 → 列表行 DTO。
     *
     * @param model 分页行读模型
     * @return 列表 DTO；model 为 null 时返回 null
     */
    @Override
    public SpecimenDefinitionDto toDto(SpecimenDefinitionPageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        SpecimenDefinitionDto dto = new SpecimenDefinitionDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setTypeEnum(model.getTypeEnum());
        dto.setTypeEnumText(codeSystemQueryService.getDisplayByCode(SpecimenType.class, model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        dto.setUpdatedAt(model.getUpdatedAt());
        return dto;
    }

    /**
     * 详情读模型 → 详情 DTO。
     *
     * @param model 详情读模型
     * @return 详情 DTO；model 为 null 时返回 null
     */
    @Override
    public SpecimenDefinitionDto toDto(SpecimenDefinitionDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        SpecimenDefinitionDto dto = new SpecimenDefinitionDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setTypeEnum(model.getTypeEnum());
        dto.setTypeEnumText(codeSystemQueryService.getDisplayByCode(SpecimenType.class, model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        return dto;
    }

    /**
     * 关键字检索行 → 下拉 DTO。
     *
     * @param model 检索行读模型
     * @return 检索 DTO；model 为 null 时返回 null
     */
    @Override
    public SpecimenDefinitionDto toDto(SpecimenDefinitionSearchItemQueryModel model) {
        if (model == null) {
            return null;
        }
        SpecimenDefinitionDto dto = new SpecimenDefinitionDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setTypeEnum(model.getTypeEnum());
        dto.setTypeEnumText(codeSystemQueryService.getDisplayByCode(SpecimenType.class, model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        return dto;
    }

    /**
     * 创建请求 → 领域新建命令。
     *
     * @param request HTTP 创建请求
     * @return 新建命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public CreateSpecimenDefinitionCommand toCreateCommand(CreateSpecimenDefinitionRequest request) {
        if (request == null) {
            throw new ValidationException("新建样本定义失败：请求体不能为空");
        }
        CreateSpecimenDefinitionCommand command = new CreateSpecimenDefinitionCommand();
        command.setBusNo(request.getBusNo());
        command.setName(request.getName());
        command.setTypeEnum(request.getTypeEnum());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        return command;
    }

    /**
     * 更新请求 → 领域维护命令。
     *
     * @param request HTTP 更新请求
     * @return 维护命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public UpdateSpecimenDefinitionCommand toUpdateCommand(UpdateSpecimenDefinitionRequest request) {
        if (request == null) {
            throw new ValidationException("维护样本定义失败：请求体不能为空");
        }
        UpdateSpecimenDefinitionCommand command = new UpdateSpecimenDefinitionCommand();
        command.setId(request.getId());
        command.setBusNo(request.getBusNo());
        command.setName(request.getName());
        command.setTypeEnum(request.getTypeEnum());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        return command;
    }
}
