/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.wardbed.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.application.basic.wardbed.dto.WardBedDto;
import com.zxplat.application.basic.wardbed.request.CreateWardBedRequest;
import com.zxplat.application.basic.wardbed.request.UpdateWardBedRequest;
import com.zxplat.domain.administration.location.command.ActivateLocationCommand;
import com.zxplat.domain.administration.location.command.AbolishLocationCommand;
import com.zxplat.domain.administration.location.command.DeactivateLocationCommand;
import com.zxplat.domain.administration.location.command.EditLocationInfoCommand;
import com.zxplat.domain.administration.location.command.RegisterLocationCommand;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.share.constants.LocationTypeJsonConstants;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.LocationMode;
import com.zxplat.domain.share.enums.LocationStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.LocationTypeJsonUtil;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 病区/病房/床位映射：请求与响应使用枚举 {@code code} 字符串，经 {@link EnumUtil} 校验后写入领域命令。
 *
 * <p>病区多科挂靠：{@code organizationIds} → {@code type_json.servedOrganizationIds}，出参结构化回填。</p>
 */
@Component
@RequiredArgsConstructor
public class WardBedMapperImpl implements WardBedMapper {

    /** 读写 type_json 多科挂靠。 */
    private final ObjectMapper objectMapper;

    /**
     * 将位置实体映射为应用层 DTO，并填充状态、形态展示名与病区挂靠科室集合。
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
        dto.setParentId(entity.getParentId());
        dto.setDisplayOrder(entity.getDisplayOrder());
        // 仅病区回填多科挂靠集合，供维护页多选回显
        if (LocationForm.WARD.getCode().equals(entity.getFormEnum())) {
            dto.setOrganizationIds(this.resolveOrganizationIdsForWard(entity));
        }
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
        item.setTypeJson(this.resolveTypeJsonForWrite(request));
        item.setOrganizationId(request.getOrganizationId());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setRegisterAsActive(Boolean.TRUE);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 将更新请求转为编辑命令，并声明变更字段白名单（含主挂靠与 type_json）。
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
        item.setTypeJson(this.resolveTypeJsonForWrite(request));
        item.setOrganizationId(request.getOrganizationId());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setChangedFields(List.of(
            "name", "formEnum", "modeEnum", "typeJson", "displayOrder", "pyStr", "wbStr", "organizationId"));
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
     * 组装病区挂靠科室全集：主挂靠在前，其后为 type_json 附加科（去重保序）。
     *
     * @param ward 病区实体
     * @return 科室主键列表
     */
    private List<Long> resolveOrganizationIdsForWard(Location ward) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        if (ward.getOrganizationId() != null) {
            ordered.add(ward.getOrganizationId());
        }
        List<Long> served = LocationTypeJsonUtil.readServedOrganizationIds(
            this.objectMapper, ward.getTypeJson());
        for (Long orgId : served) {
            ordered.add(orgId);
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 写侧 type_json：病区把 organizationIds 写入 servedOrganizationIds；其它形态保留/缺省空对象。
     *
     * @param request 创建或更新请求
     * @return 可持久化 JSON
     */
    private String resolveTypeJsonForWrite(CreateWardBedRequest request) {
        String formCode = request.getFormEnum();
        if (formCode != null && LocationForm.WARD.getCode().equals(formCode.trim())) {
            List<Long> allOrgIds = this.normalizeOrganizationIds(request);
            return LocationTypeJsonUtil.writeServedOrganizationIds(
                this.objectMapper, request.getTypeJson(), allOrgIds);
        }
        if (StringUtils.isEmpty(request.getTypeJson())) {
            return LocationTypeJsonConstants.EMPTY_TYPE_JSON;
        }
        return request.getTypeJson();
    }

    /**
     * 规范化病区挂靠科室列表：优先 organizationIds，否则仅 organizationId。
     *
     * @param request 请求
     * @return 去重保序列表
     */
    private List<Long> normalizeOrganizationIds(CreateWardBedRequest request) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        if (request.getOrganizationIds() != null) {
            for (Long id : request.getOrganizationIds()) {
                if (id != null) {
                    ordered.add(id);
                }
            }
        }
        if (ordered.isEmpty() && request.getOrganizationId() != null) {
            ordered.add(request.getOrganizationId());
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 校验并返回位置形态 code。
     *
     * @param formCode 请求中的形态 code
     * @return 领域形态 code
     */
    private String requireLocationFormCode(String formCode) {
        if (StringUtils.isEmpty(formCode)) {
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
        if (StringUtils.isEmpty(modeCode)) {
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
