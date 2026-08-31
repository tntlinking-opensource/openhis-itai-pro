/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.mapper;

import com.openhis.health.application.basic.labdevice.dto.LabDeviceDto;
import com.openhis.health.application.basic.labdevice.request.CreateLabDeviceRequest;
import com.openhis.health.application.basic.labdevice.request.UpdateLabDeviceRequest;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.labdevice.command.CreateLabDeviceCommand;
import com.openhis.health.domain.administration.labdevice.command.UpdateLabDeviceCommand;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDevicePageItemQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceSearchItemQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * {@link LabDeviceMapper} 默认实现。
 */
@Component
@RequiredArgsConstructor
public class LabDeviceMapperImpl implements LabDeviceMapper {

    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final SupplierQueryService supplierQueryService;

    /**
     * 分页读模型投影为对外 DTO，并 enrich 科室/位置/厂家名。
     *
     * @param model 分页行读模型
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public LabDeviceDto toDto(LabDevicePageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        LabDeviceDto dto = mapCoreFields(model.getId(), model.getBusNo(), model.getName(), model.getPyStr(),
            model.getWbStr(), model.getStatusEnum(), model.getCategoryCode());
        dto.setOrgId(model.getOrgId());
        dto.setLocationId(model.getLocationId());
        dto.setModelNumber(model.getModelNumber());
        dto.setSerialNumber(model.getSerialNumber());
        dto.setManufacturerId(model.getManufacturerId());
        dto.setManufactureDate(model.getManufactureDate());
        dto.setExpirationDate(model.getExpirationDate());
        dto.setDescription(model.getDescription());
        dto.setUpdatedAt(model.getUpdatedAt());
        enrichDisplayNames(dto);
        return dto;
    }

    /**
     * 详情读模型投影为对外 DTO，并 enrich 科室/位置/厂家名。
     *
     * @param model 详情读模型
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public LabDeviceDto toDto(LabDeviceDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        LabDeviceDto dto = mapCoreFields(model.getId(), model.getBusNo(), model.getName(), model.getPyStr(),
            model.getWbStr(), model.getStatusEnum(), model.getCategoryCode());
        dto.setOrgId(model.getOrgId());
        dto.setLocationId(model.getLocationId());
        dto.setModelNumber(model.getModelNumber());
        dto.setSerialNumber(model.getSerialNumber());
        dto.setManufacturerId(model.getManufacturerId());
        dto.setManufactureDate(model.getManufactureDate());
        dto.setExpirationDate(model.getExpirationDate());
        dto.setDescription(model.getDescription());
        enrichDisplayNames(dto);
        return dto;
    }

    /**
     * 检索读模型投影为对外 DTO。
     *
     * @param model 检索行读模型
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public LabDeviceDto toDto(LabDeviceSearchItemQueryModel model) {
        if (model == null) {
            return null;
        }
        LabDeviceDto dto = mapCoreFields(model.getId(), model.getBusNo(), model.getName(), model.getPyStr(),
            model.getWbStr(), model.getStatusEnum(), model.getCategoryCode());
        enrichDisplayNames(dto);
        return dto;
    }

    /**
     * 新建请求转为领域命令。
     *
     * @param request 创建请求
     * @return 新建命令
     */
    @Override
    public CreateLabDeviceCommand toCreateCommand(CreateLabDeviceRequest request) {
        if (request == null) {
            throw new ValidationException("新建检验设备失败：请求体不能为空");
        }
        CreateLabDeviceCommand command = new CreateLabDeviceCommand();
        command.setName(request.getName());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        command.setOrgId(request.getOrgId());
        command.setLocationId(request.getLocationId());
        command.setCategoryCode(request.getCategoryCode());
        command.setModelNumber(request.getModelNumber());
        command.setSerialNumber(request.getSerialNumber());
        command.setManufacturerId(request.getManufacturerId());
        command.setManufactureDate(request.getManufactureDate());
        command.setExpirationDate(request.getExpirationDate());
        command.setDescription(request.getDescription());
        return command;
    }

    /**
     * 维护请求转为领域命令。
     *
     * @param request 更新请求
     * @return 更新命令
     */
    @Override
    public UpdateLabDeviceCommand toUpdateCommand(UpdateLabDeviceRequest request) {
        if (request == null) {
            throw new ValidationException("维护检验设备失败：请求体不能为空");
        }
        UpdateLabDeviceCommand command = new UpdateLabDeviceCommand();
        command.setId(request.getId());
        command.setName(request.getName());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        command.setOrgId(request.getOrgId());
        command.setLocationId(request.getLocationId());
        command.setCategoryCode(request.getCategoryCode());
        command.setModelNumber(request.getModelNumber());
        command.setSerialNumber(request.getSerialNumber());
        command.setManufacturerId(request.getManufacturerId());
        command.setManufactureDate(request.getManufactureDate());
        command.setExpirationDate(request.getExpirationDate());
        command.setDescription(request.getDescription());
        return command;
    }

    /**
     * 映射核心展示字段与枚举/术语文案。
     *
     * @param id 主键
     * @param busNo 业务编码
     * @param name 设备名称
     * @param pyStr 拼音码
     * @param wbStr 五笔码
     * @param statusEnum 发布状态 code
     * @param categoryCode 设备分类 code
     * @return 已填充核心字段的 DTO
     */
    private LabDeviceDto mapCoreFields(
        Long id,
        String busNo,
        String name,
        String pyStr,
        String wbStr,
        String statusEnum,
        String categoryCode) {
        LabDeviceDto dto = new LabDeviceDto();
        dto.setId(id);
        dto.setBusNo(busNo);
        dto.setName(name);
        dto.setPyStr(pyStr);
        dto.setWbStr(wbStr);
        dto.setStatusEnum(statusEnum);
        dto.setStatusEnumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, statusEnum));
        dto.setCategoryCode(categoryCode);
        Map<String, String> categoryLabels = terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.ADM_DEVICE_CATEGORY_CODE);
        if (categoryCode != null) {
            dto.setCategoryCodeText(categoryLabels.get(categoryCode));
        }
        return dto;
    }

    /**
     * 批量 enrich 科室、位置、生产厂家展示名（单条 DTO 时仍走批量接口）。
     *
     * @param dto 待 enrich 的 DTO
     */
    private void enrichDisplayNames(LabDeviceDto dto) {
        if (dto.getOrgId() != null) {
            Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(
                Collections.singletonList(dto.getOrgId()));
            dto.setOrgName(orgNames.get(dto.getOrgId()));
        }
        if (dto.getLocationId() != null) {
            Map<Long, String> locationNames = locationQueryService.findLocationNamesByIds(
                Collections.singletonList(dto.getLocationId()));
            dto.setLocationName(locationNames.get(dto.getLocationId()));
        }
        if (dto.getManufacturerId() != null) {
            Optional<String> supplierName = supplierQueryService.findSupplierNameIfPresent(dto.getManufacturerId());
            if (supplierName.isPresent()) {
                dto.setManufacturerName(supplierName.get());
            }
        }
    }
}
