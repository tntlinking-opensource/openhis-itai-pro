/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.mapper;

import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateBindingGroupDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateHeaderRowDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineEditDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineRowDto;
import com.openhis.health.application.basic.ordertemplate.request.SaveMaintainedOrderTemplateHeaderRequest;
import com.openhis.health.application.basic.ordertemplate.request.SaveOrderTemplateFromAdviceRequest;
import com.openhis.health.application.basic.ordertemplate.response.GetOrderTemplateDetailResponse;
import com.openhis.health.application.basic.ordertemplate.response.InitOrderTemplateMaintenanceResponse;
import com.openhis.health.application.basic.ordertemplate.response.PageMaintainedOrderTemplateResponse;
import com.openhis.health.application.basic.ordertemplate.response.PreviewOrderTemplateApplyResponse;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.OrderTemplateConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.custom.ordertemplate.command.AddOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.command.CreateOrderTemplateCommand;
import com.openhis.health.domain.custom.ordertemplate.command.RemoveOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.command.UpdateOrderTemplateHeaderCommand;
import com.openhis.health.domain.custom.ordertemplate.command.UpdateOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateLineQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateWithLinesQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.enums.OrderTemplateBinding;
import com.openhis.health.domain.share.enums.OrderTemplateKind;
import com.openhis.health.domain.share.enums.OrderTemplateLineKind;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.health.domain.share.enums.OrderTemplateSource;
import com.openhis.health.domain.share.enums.OrderTemplateStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link OrderTemplateMapper} 实现：字段搬运与展示枚举文本装配。
 */
@Component
public class OrderTemplateMapperImpl implements OrderTemplateMapper {

    /**
     * 由业务枚举生成维护页初始化下拉响应。
     *
     * @return 初始化响应
     */
    @Override
    public InitOrderTemplateMaintenanceResponse toInitOrderTemplateMaintenanceResponse() {
        InitOrderTemplateMaintenanceResponse response = new InitOrderTemplateMaintenanceResponse();
        response.setBindingOptions(EnumUtil.toOptions(OrderTemplateBinding.class));
        response.setSceneOptions(EnumUtil.toOptions(OrderTemplateScene.class));
        response.setTemplateKindOptions(EnumUtil.toOptions(OrderTemplateKind.class));
        response.setLineKindOptions(EnumUtil.toOptions(OrderTemplateLineKind.class));
        response.setStatusOptions(EnumUtil.toOptions(OrderTemplateStatus.class));
        return response;
    }

    /**
     * 领域分页结果映射为维护端分页响应，并按关键字过滤当前页行。
     *
     * @param page 领域分页
     * @param keyword 名称/编码关键字
     * @return 分页响应
     */
    @Override
    public PageMaintainedOrderTemplateResponse toPageMaintainedOrderTemplateResponse(
        Page<OrderTemplateHeaderQueryModel> page,
        String keyword
    ) {
        if (page == null) {
            return PagedResultBuilder.buildEmpty(new PageMaintainedOrderTemplateResponse());
        }
        List<OrderTemplateHeaderRowDto> rows = toHeaderRowDtos(page.getContent());
        List<OrderTemplateHeaderRowDto> filtered = filterHeaderRowsByKeyword(rows, keyword);
        return PagedResultBuilder.build(new PageMaintainedOrderTemplateResponse(), filtered, page);
    }

    /**
     * 头读模型列表 → 头行 DTO 列表。
     *
     * @param models 头读模型
     * @return DTO 列表
     */
    @Override
    public List<OrderTemplateHeaderRowDto> toHeaderRowDtos(List<OrderTemplateHeaderQueryModel> models) {
        List<OrderTemplateHeaderRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (OrderTemplateHeaderQueryModel model : models) {
            OrderTemplateHeaderRowDto dto = toHeaderRowDto(model);
            if (dto != null) {
                rows.add(dto);
            }
        }
        return rows;
    }

    /**
     * 头行聚合读模型 → 详情响应。
     *
     * @param model 头行读模型
     * @return 详情响应；model 为 null 时 null
     */
    @Override
    public GetOrderTemplateDetailResponse toGetOrderTemplateDetailResponse(OrderTemplateWithLinesQueryModel model) {
        if (model == null) {
            return null;
        }
        GetOrderTemplateDetailResponse response = new GetOrderTemplateDetailResponse();
        response.setHeader(toHeaderRowDto(model.getHeader()));
        List<OrderTemplateLineRowDto> lineRows = new ArrayList<>();
        if (model.getLines() != null) {
            for (OrderTemplateLineQueryModel lineModel : model.getLines()) {
                OrderTemplateLineRowDto lineDto = toLineRowDto(lineModel);
                if (lineDto != null) {
                    lineRows.add(lineDto);
                }
            }
        }
        response.setLines(lineRows);
        return response;
    }

    /**
     * 新建头 Request → CreateOrderTemplateCommand（固定 MAINTAINED 来源）。
     *
     * @param request 保存头请求
     * @param operatorId 操作人
     * @param objectId 归属对象
     * @return 创建命令
     */
    @Override
    public CreateOrderTemplateCommand toCreateOrderTemplateCommand(
        SaveMaintainedOrderTemplateHeaderRequest request,
        Long operatorId,
        Long objectId
    ) {
        CreateOrderTemplateCommand command = new CreateOrderTemplateCommand();
        command.setObjectId(objectId);
        command.setBindingEnum(request.getBindingEnum());
        command.setSceneEnum(EnumUtil.fromCode(OrderTemplateScene.class, request.getSceneEnum()));
        command.setTemplateKindEnum(request.getTemplateKindEnum());
        command.setTemplateSourceEnum(OrderTemplateSource.MAINTAINED.getCode());
        command.setTemplateCode(request.getTemplateCode());
        command.setName(request.getName());
        command.setDescription(request.getDescription());
        command.setSortWeight(request.getSortWeight());
        command.setRemark(request.getRemark());
        command.setDefaultChineseHerbsDoseQuantity(request.getDefaultChineseHerbsDoseQuantity());
        command.setDefaultSufferingFlag(request.getDefaultSufferingFlag());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 更新头 Request → UpdateOrderTemplateHeaderCommand。
     *
     * @param request 保存头请求
     * @param operatorId 操作人
     * @return 更新命令
     */
    @Override
    public UpdateOrderTemplateHeaderCommand toUpdateOrderTemplateHeaderCommand(
        SaveMaintainedOrderTemplateHeaderRequest request,
        Long operatorId
    ) {
        UpdateOrderTemplateHeaderCommand command = new UpdateOrderTemplateHeaderCommand();
        command.setOrderTemplateId(request.getOrderTemplateId());
        command.setSceneEnum(EnumUtil.fromCode(OrderTemplateScene.class, request.getSceneEnum()));
        command.setTemplateKindEnum(request.getTemplateKindEnum());
        command.setTemplateCode(request.getTemplateCode());
        command.setName(request.getName());
        command.setDescription(request.getDescription());
        command.setSortWeight(request.getSortWeight());
        command.setRemark(request.getRemark());
        command.setDefaultChineseHerbsDoseQuantity(request.getDefaultChineseHerbsDoseQuantity());
        command.setDefaultSufferingFlag(request.getDefaultSufferingFlag());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 行编辑 DTO → 新增行命令。
     *
     * @param line 行编辑项
     * @param orderTemplateId 头 id
     * @param operatorId 操作人
     * @return 新增命令
     */
    @Override
    public AddOrderTemplateLineCommand toAddLineCommand(
        OrderTemplateLineEditDto line,
        Long orderTemplateId,
        Long operatorId
    ) {
        AddOrderTemplateLineCommand command = new AddOrderTemplateLineCommand();
        command.setOrderTemplateId(orderTemplateId);
        copyLineFields(line, command);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 行编辑 DTO → 更新行命令。
     *
     * @param line 行编辑项
     * @param operatorId 操作人
     * @return 更新命令
     */
    @Override
    public UpdateOrderTemplateLineCommand toUpdateLineCommand(OrderTemplateLineEditDto line, Long operatorId) {
        UpdateOrderTemplateLineCommand command = new UpdateOrderTemplateLineCommand();
        command.setLineId(line.getLineId());
        command.setGroupKey(line.getGroupKey());
        command.setSortOrder(line.getSortOrder());
        command.setDefaultQuantity(line.getDefaultQuantity());
        command.setDefaultUnitCode(line.getDefaultUnitCode());
        command.setDefaultDays(line.getDefaultDays());
        command.setFrequencyId(line.getFrequencyId());
        command.setRouteCode(line.getRouteCode());
        command.setDosageText(line.getDosageText());
        command.setDefaultPerformOrgId(line.getDefaultPerformOrgId());
        command.setLineRemark(line.getLineRemark());
        command.setChineseHerbsDoseQuantity(line.getChineseHerbsDoseQuantity());
        command.setSufferingFlag(line.getSufferingFlag());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 行 id → 移除行命令。
     *
     * @param lineId 行 id
     * @param operatorId 操作人
     * @return 移除命令
     */
    @Override
    public RemoveOrderTemplateLineCommand toRemoveLineCommand(Long lineId, Long operatorId) {
        RemoveOrderTemplateLineCommand command = new RemoveOrderTemplateLineCommand();
        command.setLineId(lineId);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 全量对齐 diff：提交集合相对存量计算增删改。
     *
     * @param submitted 提交行
     * @param existing 存量行
     * @return diff 结果
     */
    @Override
    public LineDiffResult diffLines(List<OrderTemplateLineEditDto> submitted, List<OrderTemplateLineQueryModel> existing) {
        List<Long> removeIds = new ArrayList<>();
        List<OrderTemplateLineEditDto> updateLines = new ArrayList<>();
        List<OrderTemplateLineEditDto> addLines = new ArrayList<>();
        Map<Long, OrderTemplateLineQueryModel> existingById = new HashMap<>();
        if (existing != null) {
            for (OrderTemplateLineQueryModel line : existing) {
                if (line != null && line.getId() != null) {
                    existingById.put(line.getId(), line);
                }
            }
        }
        Set<Long> retainedIds = new HashSet<>();
        if (submitted != null) {
            for (OrderTemplateLineEditDto line : submitted) {
                if (line == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(line.getDeleted())) {
                    if (line.getLineId() != null) {
                        removeIds.add(line.getLineId());
                    }
                    continue;
                }
                if (line.getLineId() == null) {
                    addLines.add(line);
                } else {
                    updateLines.add(line);
                    retainedIds.add(line.getLineId());
                }
            }
        }
        for (Long existingId : existingById.keySet()) {
            if (!retainedIds.contains(existingId) && !removeIds.contains(existingId)) {
                removeIds.add(existingId);
            }
        }
        return new LineDiffResult(removeIds, updateLines, addLines);
    }

    /**
     * 四段绑定头列表 → 绑定分组 DTO，并按 searchKey 过滤各段。
     *
     * @param personalList 个人列表
     * @param departmentList 科室列表
     * @param hospitalList 全院列表
     * @param agreementList 协定处方列表
     * @param searchKey 名称关键字
     * @return 分段 DTO
     */
    @Override
    public OrderTemplateBindingGroupDto toListBindingGroupResponse(
        List<OrderTemplateHeaderQueryModel> personalList,
        List<OrderTemplateHeaderQueryModel> departmentList,
        List<OrderTemplateHeaderQueryModel> hospitalList,
        List<OrderTemplateHeaderQueryModel> agreementList,
        String searchKey
    ) {
        OrderTemplateBindingGroupDto dto = new OrderTemplateBindingGroupDto();
        dto.setPersonalList(filterHeaderRowsByKeyword(toHeaderRowDtos(personalList), normalizeSearchKey(searchKey)));
        dto.setDepartmentList(filterHeaderRowsByKeyword(toHeaderRowDtos(departmentList), normalizeSearchKey(searchKey)));
        dto.setHospitalList(filterHeaderRowsByKeyword(toHeaderRowDtos(hospitalList), normalizeSearchKey(searchKey)));
        dto.setAgreementList(filterHeaderRowsByKeyword(toHeaderRowDtos(agreementList), normalizeSearchKey(searchKey)));
        return dto;
    }

    /**
     * 头行读模型 → 预览套用响应。
     *
     * @param model 头行读模型
     * @return 预览响应
     */
    @Override
    public PreviewOrderTemplateApplyResponse toPreviewOrderTemplateApplyResponse(OrderTemplateWithLinesQueryModel model) {
        GetOrderTemplateDetailResponse detail = toGetOrderTemplateDetailResponse(model);
        if (detail == null) {
            return null;
        }
        PreviewOrderTemplateApplyResponse response = new PreviewOrderTemplateApplyResponse();
        response.setHeader(detail.getHeader());
        response.setLines(detail.getLines());
        return response;
    }

    /**
     * 断言组套头处于可预览/套用状态，且 kind/source/scene 与入口一致。
     *
     * @param header 组套头
     * @param applyContextCode 套用上下文
     * @param encounterScene 就诊场景
     * @param workstationKindCode 工作站类型
     */
    @Override
    public void assertPreviewable(
        OrderTemplateHeaderQueryModel header,
        String applyContextCode,
        OrderTemplateScene encounterScene,
        String workstationKindCode
    ) {
        if (header == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套不存在或已删除");
        }
        if (!OrderTemplateStatus.ACTIVE.getCode().equals(header.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可套用");
        }
        String normalizedContext = normalizeApplyContext(applyContextCode);
        if (OrderTemplateConstants.ApplyContext.DOCTOR.equals(normalizedContext)) {
            assertDoctorPreviewable(header, encounterScene, workstationKindCode);
            return;
        }
        if (OrderTemplateConstants.ApplyContext.NURSE_PRICING.equals(normalizedContext)) {
            assertNursePricingPreviewable(header, encounterScene);
            return;
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "套用上下文不合法");
    }

    /**
     * 另存医嘱模板 → 创建头命令（FROM_ADVICE + 请求指定的个人/科室归属）。
     */
    @Override
    public CreateOrderTemplateCommand toCreateOrderTemplateFromAdviceCommand(
        SaveOrderTemplateFromAdviceRequest request,
        OrderTemplateScene scene,
        String templateKindEnum,
        String bindingEnum,
        Long objectId,
        Long operatorId
    ) {
        CreateOrderTemplateCommand command = new CreateOrderTemplateCommand();
        command.setObjectId(objectId);
        // 绑定类型由 AppService Gate 校验后传入，此处不再二次推导
        command.setBindingEnum(bindingEnum);
        command.setSceneEnum(scene);
        command.setTemplateKindEnum(templateKindEnum);
        command.setTemplateSourceEnum(OrderTemplateSource.FROM_ADVICE.getCode());
        command.setName(request.getTemplateName());
        command.setSortWeight(0);
        command.setOperatorId(operatorId);
        if (OrderTemplateKind.TCM.getCode().equals(templateKindEnum)) {
            command.setDefaultChineseHerbsDoseQuantity(java.math.BigDecimal.ONE);
            command.setDefaultSufferingFlag(Boolean.FALSE);
        }
        return command;
    }

    /**
     * 由源医嘱是否含 TCM 药推导模板种类。
     */
    @Override
    public String deriveTemplateKindFromAdviceLines(boolean anyTcm) {
        if (anyTcm) {
            return OrderTemplateKind.TCM.getCode();
        }
        return OrderTemplateKind.GENERAL.getCode();
    }

    /**
     * 药品申请字段 → 新增组套行命令。
     */
    @Override
    public void fillAddOrderTemplateLineFromMedicationAdvice(
        AddOrderTemplateLineCommand command,
        Long medicationDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        String routeCode,
        Long frequencyId,
        Integer defaultDays,
        String dosageText,
        String groupKey,
        Integer sortOrder,
        java.math.BigDecimal chineseHerbsDoseQuantity,
        Boolean sufferingFlag,
        Long performOrgId,
        String lineRemark
    ) {
        command.setLineKindEnum(OrderTemplateLineKind.MEDICATION.getCode());
        command.setMedicationDefinitionId(medicationDefinitionId);
        command.setDefaultQuantity(quantity);
        command.setDefaultUnitCode(unitCode);
        command.setRouteCode(routeCode);
        command.setFrequencyId(frequencyId);
        command.setDefaultDays(defaultDays);
        command.setDosageText(dosageText);
        command.setGroupKey(groupKey);
        command.setSortOrder(sortOrder);
        command.setChineseHerbsDoseQuantity(chineseHerbsDoseQuantity);
        command.setSufferingFlag(sufferingFlag);
        command.setDefaultPerformOrgId(performOrgId);
        command.setLineRemark(lineRemark);
    }

    /**
     * 诊疗申请字段 → 新增组套行命令。
     */
    @Override
    public void fillAddOrderTemplateLineFromServiceAdvice(
        AddOrderTemplateLineCommand command,
        Long activityDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        Long performOrgId,
        Integer sortOrder,
        String lineRemark
    ) {
        command.setLineKindEnum(OrderTemplateLineKind.SERVICE.getCode());
        command.setActivityDefinitionId(activityDefinitionId);
        command.setDefaultQuantity(quantity);
        command.setDefaultUnitCode(unitCode);
        command.setDefaultPerformOrgId(performOrgId);
        command.setSortOrder(sortOrder);
        command.setLineRemark(lineRemark);
    }

    /**
     * 耗材申请字段 → 新增组套行命令。
     */
    @Override
    public void fillAddOrderTemplateLineFromDeviceAdvice(
        AddOrderTemplateLineCommand command,
        Long deviceDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        Long performOrgId,
        Integer sortOrder,
        String lineRemark
    ) {
        command.setLineKindEnum(OrderTemplateLineKind.DEVICE.getCode());
        command.setDeviceDefinitionId(deviceDefinitionId);
        command.setDefaultQuantity(quantity);
        command.setDefaultUnitCode(unitCode);
        command.setDefaultPerformOrgId(performOrgId);
        command.setSortOrder(sortOrder);
        command.setLineRemark(lineRemark);
    }

    /**
     * 医生站套用：kind/source/scene 与工作站校验。
     */
    private void assertDoctorPreviewable(
        OrderTemplateHeaderQueryModel header,
        OrderTemplateScene encounterScene,
        String workstationKindCode
    ) {
        if (StringUtils.isEmpty(workstationKindCode)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套套用参数后重试");
        }
        String normalizedWorkstation = workstationKindCode.trim().toUpperCase();
        if (!OrderTemplateConstants.WorkstationKind.OUTPATIENT.equals(normalizedWorkstation)
            && !OrderTemplateConstants.WorkstationKind.INPATIENT.equals(normalizedWorkstation)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套套用参数后重试");
        }
        if (OrderTemplateKind.PRICING.getCode().equals(header.getTemplateKindEnum())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "当前组套不可套用");
        }
        assertHeaderSceneMatchesEncounter(header, encounterScene);
        if (encounterScene == OrderTemplateScene.OUTPATIENT
            && !OrderTemplateConstants.WorkstationKind.OUTPATIENT.equals(normalizedWorkstation)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "就诊与工作站类型不一致");
        }
        if (encounterScene == OrderTemplateScene.INPATIENT
            && !OrderTemplateConstants.WorkstationKind.INPATIENT.equals(normalizedWorkstation)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "就诊与工作站类型不一致");
        }
    }

    /**
     * 护士站划价套用：PRICING + MAINTAINED + 住院场景。
     */
    private void assertNursePricingPreviewable(
        OrderTemplateHeaderQueryModel header,
        OrderTemplateScene encounterScene
    ) {
        if (!OrderTemplateKind.PRICING.getCode().equals(header.getTemplateKindEnum())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可套用");
        }
        if (!OrderTemplateSource.MAINTAINED.getCode().equals(header.getTemplateSourceEnum())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可套用");
        }
        if (header.getSceneEnum() != OrderTemplateScene.INPATIENT
            && header.getSceneEnum() != OrderTemplateScene.COMMON) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可套用");
        }
        if (encounterScene != null && encounterScene != OrderTemplateScene.INPATIENT) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "当前组套不可套用");
        }
    }

    /**
     * 头 scene 须为 COMMON 或与就诊 scene 一致。
     */
    private void assertHeaderSceneMatchesEncounter(
        OrderTemplateHeaderQueryModel header,
        OrderTemplateScene encounterScene
    ) {
        if (encounterScene == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "请检查组套套用参数后重试");
        }
        if (header.getSceneEnum() == OrderTemplateScene.COMMON) {
            return;
        }
        if (header.getSceneEnum() != encounterScene) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前组套不可套用");
        }
    }

    /**
     * 套用上下文 code 归一。
     */
    private String normalizeApplyContext(String applyContextCode) {
        if (StringUtils.isEmpty(applyContextCode)) {
            return null;
        }
        return applyContextCode.trim().toUpperCase();
    }

    /**
     * searchKey trim；空白 → null。
     */
    private String normalizeSearchKey(String searchKey) {
        if (StringUtils.isEmpty(searchKey)) {
            return null;
        }
        return searchKey.trim();
    }

    /**
     * 单条头读模型 → 头行 DTO。
     *
     * @param model 头读模型
     * @return DTO；null 入参返回 null
     */
    private OrderTemplateHeaderRowDto toHeaderRowDto(OrderTemplateHeaderQueryModel model) {
        if (model == null) {
            return null;
        }
        OrderTemplateHeaderRowDto dto = new OrderTemplateHeaderRowDto();
        dto.setOrderTemplateId(model.getId());
        dto.setName(model.getName());
        dto.setTemplateCode(model.getTemplateCode());
        dto.setBindingEnum(model.getBindingEnum());
        dto.setBindingEnumText(resolveEnumDisplay(OrderTemplateBinding.class, model.getBindingEnum()));
        if (model.getSceneEnum() != null) {
            dto.setSceneEnum(model.getSceneEnum().getCode());
        }
        dto.setSceneEnumText(resolveEnumDisplay(OrderTemplateScene.class, dto.getSceneEnum()));
        dto.setTemplateKindEnum(model.getTemplateKindEnum());
        dto.setTemplateKindEnumText(resolveEnumDisplay(OrderTemplateKind.class, model.getTemplateKindEnum()));
        dto.setTemplateSourceEnum(model.getTemplateSourceEnum());
        dto.setTemplateSourceEnumText(resolveEnumDisplay(OrderTemplateSource.class, model.getTemplateSourceEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(resolveEnumDisplay(OrderTemplateStatus.class, model.getStatusEnum()));
        dto.setSortWeight(model.getSortWeight());
        dto.setDefaultChineseHerbsDoseQuantity(model.getDefaultChineseHerbsDoseQuantity());
        dto.setDefaultSufferingFlag(model.getDefaultSufferingFlag());
        dto.setDescription(model.getDescription());
        dto.setRemark(model.getRemark());
        return dto;
    }

    /**
     * 单条行读模型 → 行 DTO。
     *
     * @param model 行读模型
     * @return DTO
     */
    private OrderTemplateLineRowDto toLineRowDto(OrderTemplateLineQueryModel model) {
        if (model == null) {
            return null;
        }
        OrderTemplateLineRowDto dto = new OrderTemplateLineRowDto();
        dto.setLineId(model.getId());
        dto.setLineKindEnum(model.getLineKindEnum());
        dto.setLineKindEnumText(resolveEnumDisplay(OrderTemplateLineKind.class, model.getLineKindEnum()));
        dto.setMedicationDefinitionId(model.getMedicationDefinitionId());
        dto.setActivityDefinitionId(model.getActivityDefinitionId());
        dto.setDeviceDefinitionId(model.getDeviceDefinitionId());
        dto.setProductTableName(resolveProductTableName(model.getLineKindEnum()));
        dto.setGroupKey(model.getGroupKey());
        dto.setSortOrder(model.getSortOrder());
        dto.setDefaultQuantity(model.getDefaultQuantity());
        dto.setDefaultUnitCode(model.getDefaultUnitCode());
        dto.setDefaultDays(model.getDefaultDays());
        dto.setFrequencyId(model.getFrequencyId());
        dto.setRouteCode(model.getRouteCode());
        dto.setDosageText(model.getDosageText());
        dto.setDefaultPerformOrgId(model.getDefaultPerformOrgId());
        dto.setLineRemark(model.getLineRemark());
        dto.setChineseHerbsDoseQuantity(model.getChineseHerbsDoseQuantity());
        dto.setSufferingFlag(model.getSufferingFlag());
        return dto;
    }

    /**
     * 复制行编辑字段到新增命令。
     *
     * @param line 编辑 DTO
     * @param command 新增命令
     */
    private void copyLineFields(OrderTemplateLineEditDto line, AddOrderTemplateLineCommand command) {
        command.setLineKindEnum(line.getLineKindEnum());
        command.setMedicationDefinitionId(line.getMedicationDefinitionId());
        command.setActivityDefinitionId(line.getActivityDefinitionId());
        command.setDeviceDefinitionId(line.getDeviceDefinitionId());
        command.setGroupKey(line.getGroupKey());
        command.setSortOrder(line.getSortOrder());
        command.setDefaultQuantity(line.getDefaultQuantity());
        command.setDefaultUnitCode(line.getDefaultUnitCode());
        command.setDefaultDays(line.getDefaultDays());
        command.setFrequencyId(line.getFrequencyId());
        command.setRouteCode(line.getRouteCode());
        command.setDosageText(line.getDosageText());
        command.setDefaultPerformOrgId(line.getDefaultPerformOrgId());
        command.setLineRemark(line.getLineRemark());
        command.setChineseHerbsDoseQuantity(line.getChineseHerbsDoseQuantity());
        command.setSufferingFlag(line.getSufferingFlag());
    }

    /**
     * 当前页头行按名称/编码关键字子串过滤（不区分大小写）。
     *
     * @param rows 原始行
     * @param keyword 关键字
     * @return 过滤后列表
     */
    private List<OrderTemplateHeaderRowDto> filterHeaderRowsByKeyword(List<OrderTemplateHeaderRowDto> rows, String keyword) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        if (StringUtils.isEmpty(keyword)) {
            return rows;
        }
        String trimmedKeyword = keyword.trim();
        List<OrderTemplateHeaderRowDto> filtered = new ArrayList<>();
        for (OrderTemplateHeaderRowDto row : rows) {
            if (row == null) {
                continue;
            }
            boolean nameMatched = matchesCaseInsensitiveLike(row.getName(), trimmedKeyword);
            boolean codeMatched = matchesCaseInsensitiveLike(row.getTemplateCode(), trimmedKeyword);
            if (nameMatched || codeMatched) {
                filtered.add(row);
            }
        }
        // B.2.1：当前页内存过滤后，精确全等（名称/模板码）置顶
        filtered.sort((left, right) -> {
            int exactCmp = KeywordExactFirstOrderSupport.compareExactFirst(
                trimmedKeyword,
                List.of(left.getName(), left.getTemplateCode()),
                List.of(right.getName(), right.getTemplateCode()));
            if (exactCmp != 0) {
                return exactCmp;
            }
            return 0;
        });
        return filtered;
    }

    /**
     * 内存子串匹配：与 B.2.1 {@link SearchCodeUtil#toCaseInsensitiveLikePattern} 语义一致。
     *
     * @param fieldValue 待匹配列值
     * @param trimmedKeyword 已 trim 的用户关键字
     * @return 是否匹配
     */
    private boolean matchesCaseInsensitiveLike(String fieldValue, String trimmedKeyword) {
        if (fieldValue == null || StringUtils.isEmpty(trimmedKeyword)) {
            return false;
        }
        String likePattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedKeyword);
        String needle = extractLikeNeedle(likePattern);
        return fieldValue.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * 从 {@code %keyword%} 模式串提取小写 needle。
     *
     * @param likePattern {@link SearchCodeUtil#toCaseInsensitiveLikePattern} 返回值
     * @return 小写子串
     */
    private String extractLikeNeedle(String likePattern) {
        String percent = GeneralSharedConstants.LIKE_PERCENT;
        if (likePattern.startsWith(percent)
            && likePattern.endsWith(percent)
            && likePattern.length() >= 2 * percent.length()) {
            return likePattern.substring(percent.length(), likePattern.length() - percent.length());
        }
        return likePattern;
    }

    /**
     * 行类型 → 目录表名。
     *
     * @param lineKindEnum 行类型 code
     * @return 表名或 null
     */
    private String resolveProductTableName(String lineKindEnum) {
        OrderTemplateLineKind kind = EnumUtil.fromCode(OrderTemplateLineKind.class, lineKindEnum);
        if (kind == OrderTemplateLineKind.MEDICATION) {
            return TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        if (kind == OrderTemplateLineKind.SERVICE) {
            return TableNameConstants.WKF_ACTIVITY_DEFINITION;
        }
        if (kind == OrderTemplateLineKind.DEVICE) {
            return TableNameConstants.ADM_DEVICE_DEFINITION;
        }
        return null;
    }

    /**
     * 枚举 code → 展示名。
     *
     * @param enumClass 枚举类
     * @param code 持久化 code
     * @return 展示名
     */
    private <E extends Enum<E> & com.openhis.health.domain.share.enums.EnumInterface> String resolveEnumDisplay(
        Class<E> enumClass,
        String code
    ) {
        E item = EnumUtil.fromCode(enumClass, code);
        if (item == null) {
            return null;
        }
        return item.getDisplay();
    }
}
