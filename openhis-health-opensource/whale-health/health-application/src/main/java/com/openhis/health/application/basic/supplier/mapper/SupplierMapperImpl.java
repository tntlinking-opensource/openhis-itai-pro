/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.supplier.mapper;

import com.openhis.health.application.basic.supplier.dto.SupplierDto;
import com.openhis.health.application.basic.supplier.request.CreateSupplierRequest;
import com.openhis.health.application.basic.supplier.request.UpdateSupplierRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.custom.supplier.command.MaintainSupplierInfoCommand;
import com.openhis.health.domain.custom.supplier.command.RegisterSupplierCommand;
import com.openhis.health.domain.custom.supplier.entity.Supplier;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

/**
 * {@link SupplierMapper} 实现：字段直拷；类型/状态等整型展示字段由字符串安全解析为 {@link Integer}。
 */
@Component
public class SupplierMapperImpl implements SupplierMapper {
    /**
     * 装配注册命令，含机构、联系方式与账户信息。
     *
     * @param request 创建请求，null 时返回 null
     * @return 注册命令
     */
    @Override
    public RegisterSupplierCommand ConverterRegisterSupplierCommand(CreateSupplierRequest request) {
        if (request == null) {
            return null;
        }

        RegisterSupplierCommand command = new RegisterSupplierCommand();
        command.setBusNo(request.getBusNo());
        command.setName(request.getName());
        command.setTypeEnum(request.getTypeEnum());
        command.setCreditCode(request.getCreditCode());
        command.setLegalRepresentative(request.getLegalRepresentative());
        command.setAddress(request.getAddress());
        command.setPhone(request.getPhone());
        command.setEmail(request.getEmail());
        command.setBankName(request.getBankName());
        command.setBankAccount(request.getBankAccount());
        command.setOrgId(request.getOrgId());
        command.setNote(request.getNote());
        return command;
    }

    /**
     * 装配维护命令；展示顺序当前固定传 null 由领域默认。
     *
     * @param request 编辑请求，null 时返回 null
     * @return 维护命令
     */
    @Override
    public MaintainSupplierInfoCommand ConverterMaintainSupplierInfoCommand(UpdateSupplierRequest request) {
        if (request == null) {
            return null;
        }

        MaintainSupplierInfoCommand command = new MaintainSupplierInfoCommand();
        command.setSupplierId(request.getId());
        command.setName(request.getName());
        command.setTypeEnum(request.getTypeEnum());
        command.setLegalRepresentative(request.getLegalRepresentative());
        command.setAddress(request.getAddress());
        command.setPhone(request.getPhone());
        command.setEmail(request.getEmail());
        command.setBankName(request.getBankName());
        command.setBankAccount(request.getBankAccount());
        command.setNote(request.getNote());
        command.setDisplayOrder(null);
        return command;
    }

    /**
     * 聚合根转 DTO，枚举类字符串字段尝试解析为 Integer。
     *
     * @param supplier 供应商实体，null 时返回 null
     * @return DTO
     */
    @Override
    public SupplierDto ConverterSupplierDto(Supplier supplier) {
        if (supplier == null) {
            return null;
        }

        SupplierDto supplierDto = new SupplierDto();
        supplierDto.setId(supplier.getId());
        supplierDto.setBusNo(supplier.getBusNo());
        supplierDto.setName(supplier.getName());
        supplierDto.setTypeEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(SupplierType.class, supplier.getTypeEnum()));
        supplierDto.setAddress(supplier.getAddress());
        supplierDto.setCreditCode(supplier.getCreditCode());
        supplierDto.setLegalRepresentative(supplier.getLegalRepresentative());
        supplierDto.setPyStr(supplier.getPyStr());
        supplierDto.setWbStr(supplier.getWbStr());
        supplierDto.setPhone(supplier.getPhone());
        supplierDto.setEmail(supplier.getEmail());
        fillStatusFields(supplierDto, supplier.getStatusEnum());
        supplierDto.setOrgId(supplier.getOrgId());
        supplierDto.setBankName(supplier.getBankName());
        supplierDto.setBankAccount(supplier.getBankAccount());
        supplierDto.setNote(supplier.getNote());
        return supplierDto;
    }

    /**
     * 详情查询模型转 DTO。
     *
     * @param supplier 详情模型，null 时返回 null
     * @return DTO
     */
    @Override
    public SupplierDto ConverterSupplierDto(SupplierDetailQueryModel supplier) {
        if (supplier == null) {
            return null;
        }
        SupplierDto dto = new SupplierDto();
        dto.setId(supplier.getId());
        dto.setBusNo(supplier.getBusNo());
        dto.setName(supplier.getName());
        dto.setTypeEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(SupplierType.class, supplier.getTypeEnum()));
        dto.setAddress(supplier.getAddress());
        dto.setCreditCode(supplier.getCreditCode());
        dto.setLegalRepresentative(supplier.getLegalRepresentative());
        dto.setPyStr(supplier.getPyStr());
        dto.setWbStr(supplier.getWbStr());
        dto.setPhone(supplier.getPhone());
        dto.setEmail(supplier.getEmail());
        fillStatusFields(dto, supplier.getStatusEnum());
        dto.setOrgId(supplier.getOrgId());
        dto.setBankName(supplier.getBankName());
        dto.setBankAccount(supplier.getBankAccount());
        dto.setNote(supplier.getNote());
        return dto;
    }

    /**
     * 列表行转 DTO（仅核心展示字段）。
     *
     * @param supplier 列表项，null 时返回 null
     * @return DTO
     */
    @Override
    public SupplierDto ConverterSupplierDto(SupplierListItemQueryModel supplier) {
        if (supplier == null) {
            return null;
        }
        SupplierDto dto = new SupplierDto();
        dto.setId(supplier.getId());
        dto.setBusNo(supplier.getBusNo());
        dto.setName(supplier.getName());
        dto.setTypeEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(SupplierType.class, supplier.getTypeEnum()));
        fillStatusFields(dto, supplier.getStatusEnum());
        dto.setPyStr(supplier.getPyStr());
        dto.setAddress(supplier.getAddress());
        dto.setPhone(supplier.getPhone());
        dto.setEmail(supplier.getEmail());
        return dto;
    }

    /**
     * 组织节点转下拉：label 为名称，busNo 放入 OptionDto 扩展位。
     *
     * @param supplier 组织，null 时返回 null
     * @return 下拉项
     */
    @Override
    public OptionDto ConverterSupplierDto(Organization supplier) {
        if (supplier == null) {
            return null;
        }

        String supplierIdText = null;
        if (supplier.getId() != null) {
            supplierIdText = supplier.getId().toString();
        }
        OptionDto supplierDto = new OptionDto(
                null,
                supplierIdText,
                supplier.getName(),
                supplier.getBusNo(),
                null);

        return supplierDto;
    }

    /**
     * 写入状态编码及展示文案。
     */
    private void fillStatusFields(SupplierDto dto, String statusCode) {
        if (dto == null) {
            return;
        }
        dto.setStatusEnum(statusCode);
        if (statusCode == null || statusCode.isBlank()) {
            return;
        }
        for (SupplierStatus status : SupplierStatus.values()) {
            if (status.getCode().equals(statusCode)) {
                dto.setStatusEnum_enumText(status.getDisplay());
                return;
            }
        }
    }
}
