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

package com.openhis.health.application.basic.wardbed.mapper;

import com.openhis.health.application.basic.wardbed.dto.WardBedDto;
import com.openhis.health.application.basic.wardbed.request.CreateWardBedRequest;
import com.openhis.health.application.basic.wardbed.request.UpdateWardBedRequest;
import com.openhis.health.domain.administration.location.command.ActivateLocationCommand;
import com.openhis.health.domain.administration.location.command.AbolishLocationCommand;
import com.openhis.health.domain.administration.location.command.DeactivateLocationCommand;
import com.openhis.health.domain.administration.location.command.EditLocationInfoCommand;
import com.openhis.health.domain.administration.location.command.RegisterLocationCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationMode;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 病区/病房/床位映射：请求与响应使用枚举 {@code code} 字符串，经 {@link EnumUtil} 校验后写入领域命令。
 */
@Component
public class WardBedMapperImpl implements WardBedMapper {

    /**
     * 将位置实体映射为应用层 DTO，并填充状态、形态展示名。
     *
     * @param entity 位置实体
     * @return DTO；{@code entity} 为 null 时返回 null
     */
    @Override
    public WardBedDto toDto(Location entity) {
        if (entity == null) {
            return null;
        }
        WardBedDto dto = new WardBedDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setStatusEnumText(this.resolveStatusDisplay(entity.getStatusEnum()));
        dto.setFormEnum(entity.getFormEnum());
        dto.setFormEnumText(this.resolveFormDisplay(entity.getFormEnum()));
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setDisplayOrder(entity.getDisplayOrder());
        return dto;
    }

    /**
     * 将创建请求转为登记命令：单条 item，登记即启用（{@code registerAsActive=true}）。
     *
     * @param request 创建请求
     * @return 登记命令；{@code request} 为 null 时返回 null
     */
    @Override
    public RegisterLocationCommand toCreateCommand(CreateWardBedRequest request) {
        if (request == null) {
            return null;
        }
        RegisterLocationCommand command = new RegisterLocationCommand();
        RegisterLocationCommand.RegisterLocationItemCommand item = new RegisterLocationCommand.RegisterLocationItemCommand();
        item.setName(request.getName());
        item.setParentId(this.normalizeParentId(request.getParentId()));
        item.setFormEnum(this.requireLocationFormCode(request.getFormEnum()));
        item.setModeEnum(this.resolveModeCode(request.getModeEnum()));
        item.setTypeJson(this.resolveTypeJson(request.getTypeJson()));
        item.setOrganizationId(request.getOrganizationId());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setRegisterAsActive(Boolean.TRUE);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 将更新请求转为编辑命令，并声明变更字段白名单。
     *
     * @param request 更新请求
     * @return 编辑命令；{@code request} 为 null 时返回 null
     */
    @Override
    public EditLocationInfoCommand toUpdateCommand(UpdateWardBedRequest request) {
        if (request == null) {
            return null;
        }
        EditLocationInfoCommand command = new EditLocationInfoCommand();
        EditLocationInfoCommand.EditLocationInfoItemCommand item = new EditLocationInfoCommand.EditLocationInfoItemCommand();
        item.setLocationId(request.getId());
        item.setName(request.getName());
        item.setFormEnum(this.requireLocationFormCode(request.getFormEnum()));
        item.setModeEnum(this.resolveModeCode(request.getModeEnum()));
        item.setTypeJson(request.getTypeJson());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setChangedFields(List.of("name", "formEnum", "modeEnum", "typeJson", "displayOrder", "pyStr", "wbStr"));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 构造单条位置的启用命令。
     *
     * @param locationId 位置主键
     * @return 启用命令
     */
    @Override
    public ActivateLocationCommand toActivateCommand(Long locationId) {
        ActivateLocationCommand command = new ActivateLocationCommand();
        ActivateLocationCommand.ActivateLocationItemCommand item = new ActivateLocationCommand.ActivateLocationItemCommand();
        item.setLocationId(locationId);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 构造单条位置的停用命令。
     *
     * @param locationId 位置主键
     * @return 停用命令
     */
    @Override
    public DeactivateLocationCommand toDeactivateCommand(Long locationId) {
        DeactivateLocationCommand command = new DeactivateLocationCommand();
        DeactivateLocationCommand.DeactivateLocationItemCommand item =
            new DeactivateLocationCommand.DeactivateLocationItemCommand();
        item.setLocationId(locationId);
        item.setReason("应用层停用");
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 构造单条位置的废止命令。
     *
     * @param locationId 位置主键
     * @return 废止命令
     */
    @Override
    public AbolishLocationCommand toAbolishCommand(Long locationId) {
        AbolishLocationCommand command = new AbolishLocationCommand();
        AbolishLocationCommand.AbolishLocationItemCommand item = new AbolishLocationCommand.AbolishLocationItemCommand();
        item.setLocationId(locationId);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 解析位置状态展示名。
     *
     * @param statusCode 状态 code
     * @return 展示名；无法识别时返回 null
     */
    private String resolveStatusDisplay(String statusCode) {
        LocationStatus status = EnumUtil.fromCode(LocationStatus.class, statusCode);
        if (status == null) {
            return null;
        }
        return status.getDisplay();
    }

    /**
     * 解析位置形态展示名。
     *
     * @param formCode 形态 code
     * @return 展示名；无法识别时返回 null
     */
    private String resolveFormDisplay(String formCode) {
        LocationForm form = EnumUtil.fromCode(LocationForm.class, formCode);
        if (form == null) {
            return null;
        }
        return form.getDisplay();
    }

    /**
     * 类型扩展 JSON 缺省为 {@code {}}。
     *
     * @param typeJson 请求中的 JSON 字符串
     * @return 非空 JSON 字符串
     */
    private String resolveTypeJson(String typeJson) {
        if (StringUtils.isBlank(typeJson)) {
            return "{}";
        }
        return typeJson;
    }

    /**
     * 校验并返回位置形态 code。
     *
     * @param formCode 请求中的形态 code
     * @return 领域形态 code
     */
    private String requireLocationFormCode(String formCode) {
        if (StringUtils.isBlank(formCode)) {
            throw new ValidationException("物理形式不能为空");
        }
        LocationForm form = EnumUtil.fromCode(LocationForm.class, formCode.trim());
        if (form == null) {
            throw new ValidationException("物理形式不合法");
        }
        return form.getCode();
    }

    /**
     * 解析位置模式 code，缺省为 INSTANCE。
     *
     * @param modeCode 请求中的模式 code，可为空
     * @return 领域模式 code
     */
    private String resolveModeCode(String modeCode) {
        if (StringUtils.isBlank(modeCode)) {
            return LocationMode.INSTANCE.getCode();
        }
        LocationMode mode = EnumUtil.fromCode(LocationMode.class, modeCode.trim());
        if (mode == null) {
            throw new ValidationException("位置模式不合法");
        }
        return mode.getCode();
    }

    /**
     * 父级 id 规范化：0 或负数视为无父级。
     *
     * @param parentId 请求父级 id
     * @return 领域父级 id 或 null
     */
    private Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId.longValue() <= 0L) {
            return null;
        }
        return parentId;
    }
}
