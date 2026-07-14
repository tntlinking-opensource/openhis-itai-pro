/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargetype.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.chargetype.dto.ChargeTypeDto;
import com.openhis.health.application.basic.chargetype.request.CreateChargeTypeRequest;
import com.openhis.health.application.basic.chargetype.request.UpdateChargeTypeRequest;
import com.openhis.health.domain.financial.contract.command.CreateContractCommand;
import com.openhis.health.domain.financial.contract.command.EditContractCommand;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * {@link ChargeTypeMapper} 默认实现：列表/详情两路入口归一到私有装配，写侧填充领域默认字段。
 */
@Component
public class ChargeTypeMapperImpl implements ChargeTypeMapper {

    /**
     * 将合约列表行投影为费用性质 DTO；入参为 {@code null} 时返回 {@code null}。
     *
     * @param model 合约列表查询行
     * @return 费用性质展示对象
     */
    @Override
    public ChargeTypeDto toDto(ContractListItemQueryModel model) {
        // 列表接口允许缺行占位，空模型直接返回 null，由调用方决定是否过滤
        if (model == null) {
            return null;
        }
        return this.toDto(model.getId(), model.getName(), model.getBusNo(), model.getCategoryEnum(), model.getActiveFlag(), model.getDisplayOrder());
    }

    /**
     * 将合约详情查询模型投影为费用性质 DTO；入参为 {@code null} 时返回 {@code null}。
     *
     * @param model 合约详情查询模型
     * @return 费用性质展示对象
     */
    @Override
    public ChargeTypeDto toDto(ContractDetailQueryModel model) {
        // 详情与列表字段对齐，避免前端两套映射
        if (model == null) {
            return null;
        }
        return this.toDto(model.getId(), model.getName(), model.getBusNo(), model.getCategoryEnum(), model.getActiveFlag(), model.getDisplayOrder());
    }

    /**
     * 将合约核心属性压入费用性质 DTO，并统一 code 与 busNo、转换启用标志为整型约定。
     *
     * @param id             合约主键
     * @param name           名称
     * @param busNo          业务编码
     * @param categoryEnum   分类业务码
     * @param activeFlag     领域布尔启用标志
     * @param displayOrder   显示顺序
     * @return 装配完成的 {@link ChargeTypeDto}
     */
    private ChargeTypeDto toDto(Long id, String name, String busNo, String categoryEnum, Boolean activeFlag, Integer displayOrder) {
        ChargeTypeDto dto = new ChargeTypeDto();
        dto.setId(id);
        dto.setName(name);
        dto.setBusNo(busNo);
        dto.setCode(busNo);
        dto.setCategoryEnum(categoryEnum);
        dto.setActiveFlag(toActiveFlag(activeFlag));
        dto.setDisplayOrder(displayOrder);
        return dto;
    }

    /**
     * 将新增费用性质请求装配为领域创建合约命令；请求体为空时抛出校验异常。
     *
     * @param request 创建请求
     * @return 领域创建命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public CreateContractCommand toCreateCommand(CreateChargeTypeRequest request) {
        if (request == null) {
            throw new ValidationException("新增费用性质失败：请求体不能为空");
        }
        // 新建默认挂到固定机构并标记非医保合约，与历史主数据脚本约定一致
        CreateContractCommand command = new CreateContractCommand();
        command.setBusNo(trimToNull(request.getBusNo()));
        command.setName(trimToNull(request.getName()));
        command.setCategoryEnum(trimToNull(request.getCategoryEnum()));
        command.setDisplayOrder(request.getDisplayOrder());
        command.setYbFlag(Boolean.FALSE);
        command.setOrgId(1L);
        return command;
    }

    /**
     * 将编辑费用性质请求装配为领域编辑合约命令；请求体为空时抛出校验异常。
     *
     * @param request 编辑请求
     * @return 领域编辑命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public EditContractCommand toEditCommand(UpdateChargeTypeRequest request) {
        if (request == null) {
            throw new ValidationException("编辑费用性质失败：请求体不能为空");
        }
        EditContractCommand command = new EditContractCommand();
        command.setId(request.getId());
        command.setBusNo(trimToNull(request.getBusNo()));
        command.setName(trimToNull(request.getName()));
        command.setCategoryEnum(trimToNull(request.getCategoryEnum()));
        command.setDisplayOrder(request.getDisplayOrder());
        command.setYbFlag(Boolean.FALSE);
        return command;
    }

    /**
     * 将领域布尔启用标志转为前端约定的整型码：1 启用、2 停用、未知则 {@code null}。
     *
     * @param activeFlag 合约查询模型中的启用标志
     * @return 前端 {@code activeFlag} 字段值
     */
    private Integer toActiveFlag(Boolean activeFlag) {
        // 领域 true/false 映射为前端约定 1 启用、2 停用
        if (Boolean.TRUE.equals(activeFlag)) {
            return 1;
        }
        if (Boolean.FALSE.equals(activeFlag)) {
            return 2;
        }
        return null;
    }

}
