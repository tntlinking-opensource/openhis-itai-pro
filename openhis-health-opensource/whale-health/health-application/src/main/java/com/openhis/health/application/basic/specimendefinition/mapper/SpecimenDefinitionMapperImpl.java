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

package com.openhis.health.application.basic.specimendefinition.mapper;

import com.openhis.health.application.basic.specimendefinition.dto.SpecimenDefinitionDto;
import com.openhis.health.application.basic.specimendefinition.request.CreateSpecimenDefinitionRequest;
import com.openhis.health.application.basic.specimendefinition.request.UpdateSpecimenDefinitionRequest;
import com.openhis.health.domain.administration.specimendefinition.command.CreateSpecimenDefinitionCommand;
import com.openhis.health.domain.administration.specimendefinition.command.UpdateSpecimenDefinitionCommand;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionDetailQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionPageItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.SpecimenType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SpecimenDefinitionMapper} 默认实现：列表/详情/检索三路透射，枚举展示文案由术语服务解析。
 */
@Component
@RequiredArgsConstructor
public class SpecimenDefinitionMapperImpl implements SpecimenDefinitionMapper {

    private final CodeSystemQueryService codeSystemQueryService;

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
        dto.setTypeEnumText(resolveTypeText(model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(resolveStatusText(model.getStatusEnum()));
        dto.setUpdatedAt(model.getUpdatedAt());
        return dto;
    }

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
        dto.setTypeEnumText(resolveTypeText(model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(resolveStatusText(model.getStatusEnum()));
        return dto;
    }

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
        dto.setTypeEnumText(resolveTypeText(model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(resolveStatusText(model.getStatusEnum()));
        return dto;
    }

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

    private String resolveTypeText(String typeEnum) {
        return codeSystemQueryService.getDisplayByCode(SpecimenType.class, typeEnum);
    }

    private String resolveStatusText(String statusEnum) {
        return codeSystemQueryService.getDisplayByCode(PublicationStatus.class, statusEnum);
    }
}
