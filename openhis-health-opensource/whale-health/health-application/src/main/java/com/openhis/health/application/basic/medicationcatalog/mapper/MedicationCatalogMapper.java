/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.mapper;

import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationManageInitDto;
import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.domain.medication.medicationdefinition.command.ActivateMedicationDefinitionsCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.CreateMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.DiscardMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.EditMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.RetireMedicationDefinitionsCommand;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionInitQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;

import java.util.List;

/**
 * 药品目录应用层映射：请求 ↔ 领域写命令；查询/读模型 ↔ 列表与详情 DTO。
 *
 * <p>实现通常为 MapStruct 生成：字段 rename、枚举与药品读模型表名等须与领域/常量约定一致。</p>
 */
public interface MedicationCatalogMapper {

    /**
     * 新建请求 → 创建药品定义命令。
     *
     * @param request 请求体
     * @return 领域命令
     */
    CreateMedicationDefinitionCommand toCreateCommand(CreateMedicationCatalogRequest request);

    /**
     * 更新请求 → 编辑命令。
     *
     * @param request 请求体
     * @return 领域命令
     */
    EditMedicationDefinitionCommand toEditCommand(UpdateMedicationCatalogRequest request);

    /**
     * 主键列表 → 批量停用命令。
     *
     * @param ids 药品定义 id 列表
     * @return 领域命令
     */
    RetireMedicationDefinitionsCommand toRetireCommand(List<Long> ids);

    /**
     * 主键列表 → 批量启用命令。
     *
     * @param ids 药品定义 id 列表
     * @return 领域命令
     */
    ActivateMedicationDefinitionsCommand toActivateCommand(List<Long> ids);

    /**
     * 主键列表 → 批量废弃命令。
     *
     * @param ids 药品定义 id 列表
     * @return 领域命令
     */
    DiscardMedicationDefinitionCommand toDiscardCommand(List<Long> ids);

    /**
     * 领域初始化查询模型 → 前端初始化 DTO（历史路径；当前页面多用 AppService 直接拉术语）。
     *
     * @param model 初始化模型
     * @return 初始化 DTO
     */
    MedicationManageInitDto toInitDto(MedicationDefinitionInitQueryModel model);

    /**
     * 分页查询模型 → 列表行 DTO。
     *
     * @param model 列表行
     * @return DTO
     */
    MedicationDto toMedicationDto(MedicationDefinitionPageQueryModel model);

    /**
     * 领域详情查询模型 → 详情 DTO。
     *
     * @param model 详情模型
     * @return 详情 DTO 或 {@code null}
     */
    MedicationDetailDto toMedicationDetailDto(MedicationDefinitionDetailQueryModel model);
}
