/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargetype.mapper;

import com.zxplat.application.basic.chargetype.dto.ChargeTypeDto;
import com.zxplat.application.basic.chargetype.request.CreateChargeTypeRequest;
import com.zxplat.application.basic.chargetype.request.UpdateChargeTypeRequest;
import com.zxplat.domain.financial.contract.command.CreateContractCommand;
import com.zxplat.domain.financial.contract.command.EditContractCommand;
import com.zxplat.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;

/**
 * 费用性质应用层与财务「合约」读写模型之间的装配契约。
 *
 * <p>列表/详情查询模型字段口径不同，故提供两路 {@code toDto} 重载；写侧统一转为领域
 * {@link CreateContractCommand} / {@link EditContractCommand}。</p>
 */
public interface ChargeTypeMapper {

    /**
     * 将合约列表行投影为费用性质 DTO。
     *
     * @param model 列表查询行；{@code null} 时由实现返回 {@code null}
     * @return 费用性质展示对象，字段含分类码与启用标志等
     */
    ChargeTypeDto toDto(ContractListItemQueryModel model);

    /**
     * 将合约详情查询模型转为费用性质 DTO（与列表行装配字段对齐）。
     *
     * @param model 详情查询模型；{@code null} 时由实现返回 {@code null}
     * @return 费用性质展示对象
     */
    ChargeTypeDto toDto(ContractDetailQueryModel model);

    /**
     * 组装新增合约命令（费用性质创建）。
     *
     * @param request 前端入参；实现类在 {@code null} 时抛校验异常
     * @return 领域创建命令，含默认机构与医保标志等应用层约定字段
     */
    CreateContractCommand toCreateCommand(CreateChargeTypeRequest request);

    /**
     * 组装编辑合约命令（费用性质更新）。
     *
     * @param request 前端入参，须含主键；实现类在 {@code null} 时抛校验异常
     * @return 领域编辑命令
     */
    EditContractCommand toEditCommand(UpdateChargeTypeRequest request);
}
