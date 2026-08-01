/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicedevicebinding.service;

import com.openhis.health.application.basic.servicedevicebinding.assembler.ServiceDeviceBindingDisplayAssembler;
import com.openhis.health.application.basic.servicedevicebinding.dto.ServiceDeviceBindingDto;
import com.openhis.health.application.basic.servicedevicebinding.dto.ServiceDeviceBindingInitDto;
import com.openhis.health.application.basic.servicedevicebinding.dto.ServiceDeviceBindingUnitOptionsDto;
import com.openhis.health.application.basic.servicedevicebinding.mapper.ServiceDeviceBindingMapper;
import com.openhis.health.application.basic.servicedevicebinding.request.ListDeviceDefOptionsRequest;
import com.openhis.health.application.basic.servicedevicebinding.request.PageServiceDeviceBindingRequest;
import com.openhis.health.application.basic.servicedevicebinding.request.SaveServiceDeviceBindingRequest;
import com.openhis.health.application.basic.servicedevicebinding.request.ServiceDeviceBindingUnitOptionsByTargetRequest;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.converter.TextValueConverter;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.custom.servicedevicetemplate.command.DeleteBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateDomainService;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateRangeCode;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基础数据「诊疗耗材绑定」配置应用服务：WH-BND001 维护用法/诊疗来源项与诊疗或耗材目标之间的绑定关系。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ServiceDeviceTemplateQueryService}：按绑定类型与来源项分页查询</li>
 *   <li>{@link ServiceDeviceTemplateDomainService}：创建、编辑与逻辑删除绑定</li>
 *   <li>{@link ServiceDeviceBindingDisplayAssembler}：列表 enrich 与 init 来源/目标下拉</li>
 * </ul>
 *
 * <p>接入方式：由 {@code AppServiceControllerRegistrar} 注册为
 * {@code POST /api/app/serviceDeviceBinding/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceDeviceBindingAppService {

    /** 绑定 DTO 与领域命令映射。 */
    private final ServiceDeviceBindingMapper serviceDeviceBindingMapper;
    /** 列表展示 enrich 与 init 下拉装配。 */
    private final ServiceDeviceBindingDisplayAssembler serviceDeviceBindingDisplayAssembler;
    /** 服务设备模板读侧。 */
    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    /** 服务设备模板写侧。 */
    private final ServiceDeviceTemplateDomainService serviceDeviceTemplateDomainService;
    /** 写路径操作人门控 B.6.1。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 术语下拉（init 与单位选项）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 诊疗活动定义读侧：目标默认单位。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 耗材定义读侧：目标可选单位。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 返回绑定配置页初始化数据：状态、范围、绑定类型、单位、各来源候选及目标表选项。
     *
     * @return 初始化 DTO
     */
    public ServiceDeviceBindingInitDto init() {
        // Gate：只读页初始化，不写库
        // Pre：装配状态、范围、绑定类型、单位与来源/目标下拉
        // Asm：映射页初始化响应
        ServiceDeviceBindingInitDto dto = new ServiceDeviceBindingInitDto();
        List<OptionDto> statusOptions = new ArrayList<>();
        statusOptions.add(new OptionDto(
            PublicationStatus.ACTIVE.getSortOrder(),
            PublicationStatus.ACTIVE.getCode(),
            PublicationStatus.ACTIVE.getDisplay()));
        statusOptions.add(new OptionDto(
            PublicationStatus.INACTIVE.getSortOrder(),
            PublicationStatus.INACTIVE.getCode(),
            PublicationStatus.INACTIVE.getDisplay()));
        dto.setStatusOptions(statusOptions);
        // 配置页仅下发门诊/住院；全院为历史兼容码，不作为新建选项
        List<OptionDto> rangeOptions = new ArrayList<>();
        rangeOptions.add(new OptionDto(
            ServiceDeviceTemplateRangeCode.OUTPATIENT.getSortOrder(),
            ServiceDeviceTemplateRangeCode.OUTPATIENT.getCode(),
            ServiceDeviceTemplateRangeCode.OUTPATIENT.getDisplay()));
        rangeOptions.add(new OptionDto(
            ServiceDeviceTemplateRangeCode.INPATIENT.getSortOrder(),
            ServiceDeviceTemplateRangeCode.INPATIENT.getCode(),
            ServiceDeviceTemplateRangeCode.INPATIENT.getDisplay()));
        dto.setRangeOptions(rangeOptions);
        List<OptionDto> bindTypeOptions = new ArrayList<>();
        for (ServiceDeviceTemplateTypeCode type : ServiceDeviceTemplateTypeCode.values()) {
            bindTypeOptions.add(new OptionDto(type.getSortOrder(), type.getCode(), type.getDisplay()));
        }
        dto.setBindTypeOptions(bindTypeOptions);
        dto.setUnitOptions(terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE));
        dto.setUsageSourceOptions(terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.METHOD_CODE));
        dto.setActivitySourceOptions(serviceDeviceBindingDisplayAssembler.buildActivitySourceOptions());
        dto.setDeviceDefOptions(serviceDeviceBindingDisplayAssembler.buildDeviceDefOptions());
        dto.setTargetTableOptions(serviceDeviceBindingDisplayAssembler.buildTargetTableOptions());
        return dto;
    }

    /**
     * 返回绑定页耗材目标下拉（实时读库，支持关键字过滤）。
     *
     * @param request 可选关键字
     * @return 耗材定义下拉项
     */
    public List<OptionDto> listDeviceDefOptions(ListDeviceDefOptionsRequest request) {
        // Pre：解析可选关键字
        String keyword = null;
        if (request != null) {
            keyword = request.getKeyword();
        }
        // Asm：实时读库返回耗材下拉
        return serviceDeviceBindingDisplayAssembler.buildDeviceDefOptions(keyword);
    }

    /**
     * 分页查询绑定列表：可按来源项精确过滤或仅按范围+类型浏览，状态过滤可选。
     *
     * @param request 分页条件；{@code null} 时由内部补默认 {@link PageServiceDeviceBindingRequest}
     * @return 分页绑定 DTO（含展示名 enrich）
     * @throws BusinessException 绑定类型为空或不合法时
     */
    public PagedResult<ServiceDeviceBindingDto> pageBindings(PageServiceDeviceBindingRequest request) {
        PageServiceDeviceBindingRequest query = request;
        if (query == null) {
            query = new PageServiceDeviceBindingRequest();
        }
        // Gate：绑定类型为必选项
        String typeCode = null;
        if (StringUtils.isNotEmpty(query.getBindType())) {
            ServiceDeviceTemplateTypeCode type =
                EnumUtil.fromCode(ServiceDeviceTemplateTypeCode.class, query.getBindType().trim());
            if (type != null) {
                typeCode = type.getCode();
            }
        }
        if (typeCode == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定类型不能为空");
        }
        // 范围可选：有值则按门诊/住院过滤；空则同一来源下展示全部范围
        String rangeCode = null;
        if (StringUtils.isNotEmpty(query.getRangeCode())) {
            String trimmedRange = query.getRangeCode().trim();
            ServiceDeviceTemplateRangeCode range =
                EnumUtil.fromCode(ServiceDeviceTemplateRangeCode.class, trimmedRange);
            if (range == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "适用范围不合法");
            }
            rangeCode = range.getCode();
        }
        int pageIndex = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        String statusCode = null;
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            PublicationStatus status =
                EnumUtil.fromCode(PublicationStatus.class, query.getStatusEnum().trim());
            if (status != null) {
                if (PublicationStatus.ACTIVE.equals(status)) {
                    statusCode = PublicationStatus.ACTIVE.getCode();
                } else if (PublicationStatus.INACTIVE.equals(status)) {
                    statusCode = PublicationStatus.INACTIVE.getCode();
                }
            }
        }
        Page<ServiceDeviceTemplateQueryModel> page;
        // Branch：带来源 id 时按来源维度分页；否则须带范围按类型浏览
        if (StringUtils.isNotEmpty(query.getSourceId())) {
            String itemTable = null;
            if (ServiceDeviceTemplateTypeCode.USAGE.getCode().equals(typeCode)) {
                itemTable = TableNameConstants.TER_CONCEPT;
            } else if (ServiceDeviceTemplateTypeCode.ACTIVITY.getCode().equals(typeCode)) {
                itemTable = TableNameConstants.WKF_ACTIVITY_DEFINITION;
            }
            if (itemTable == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定类型不合法");
            }
            if (statusCode == null) {
                page = serviceDeviceTemplateQueryService.pageBindingsBySource(
                    rangeCode, typeCode, itemTable, query.getSourceId().trim(), PageRequest.of(pageIndex, pageSize));
            } else {
                page = serviceDeviceTemplateQueryService.pageBindingsBySourceAndStatus(
                    rangeCode, typeCode, itemTable, query.getSourceId().trim(), statusCode,
                    PageRequest.of(pageIndex, pageSize));
            }
        } else {
            if (StringUtils.isEmpty(rangeCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT,
                    "未指定来源项时须指定适用范围");
            }
            if (statusCode == null) {
                page = serviceDeviceTemplateQueryService.pageBindingsByRangeAndType(
                    rangeCode, typeCode, PageRequest.of(pageIndex, pageSize));
            } else {
                page = serviceDeviceTemplateQueryService.pageBindingsByRangeAndTypeAndStatus(
                    rangeCode, typeCode, statusCode, PageRequest.of(pageIndex, pageSize));
            }
        }
        List<ServiceDeviceBindingDto> content = new ArrayList<>();
        for (ServiceDeviceTemplateQueryModel model : page.getContent()) {
            content.add(serviceDeviceBindingMapper.toDto(model));
        }
        serviceDeviceBindingDisplayAssembler.enrichRows(content);
        // Asm：映射分页响应
        PagedResult<ServiceDeviceBindingDto> result = new PagedResult<>();
        return PagedResultBuilder.build(result, content, page);
    }

    /**
     * 保存绑定：无主键走创建，有主键走更新。
     *
     * @param request 保存请求
     * @return 新建时返回领域生成的主键；更新时返回原主键
     * @throws BusinessException 入参校验失败时
     */
    public Long saveBinding(SaveServiceDeviceBindingRequest request) {
        // Gate：保存前业务校验与写路径操作人
        this.validateSaveRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT,
            "请先登录后再保存诊疗耗材绑定");
        // Branch：无主键走创建，有主键走更新
        if (request.getId() == null) {
            return serviceDeviceTemplateDomainService.createBinding(
                serviceDeviceBindingMapper.toCreateCommand(request, operatorId));
        }
        serviceDeviceTemplateDomainService.editBinding(serviceDeviceBindingMapper.toEditCommand(request, operatorId));
        return request.getId();
    }

    /**
     * 删除单条绑定（逻辑删除）。
     *
     * @param id 绑定主键
     * @throws BusinessException id 为空时
     */
    public void deleteBinding(Long id) {
        // Gate：主键非空且须已登录操作人
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "删除失败：绑定ID不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT,
            "请先登录后再删除诊疗耗材绑定");
        DeleteBindingCommand command = new DeleteBindingCommand();
        command.setBindingId(id);
        command.setOperatorId(operatorId);
        // Cmd：逻辑删除绑定
        serviceDeviceTemplateDomainService.deleteBinding(command);
    }

    /**
     * 按绑定目标（诊疗活动定义或设备定义）解析推荐单位码与可选单位下拉，供保存前单位控件联动。
     *
     * @param request 目标表名与目标 id
     * @return 默认单位码及选项列表
     * @throws BusinessException 目标表或目标 id 不合法时
     */
    public ServiceDeviceBindingUnitOptionsDto unitOptionsByTarget(ServiceDeviceBindingUnitOptionsByTargetRequest request) {
        // Gate：目标表与目标 id 合法
        if (request == null || StringUtils.isEmpty(request.getTargetTable())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "目标表不能为空");
        }
        Long targetId = TextValueConverter.requireLong(request.getTargetId(), "目标ID不能为空");
        ServiceDeviceBindingUnitOptionsDto dto = new ServiceDeviceBindingUnitOptionsDto();
        List<OptionDto> options = new ArrayList<>();
        String preferredUnitCode = null;
        String table = request.getTargetTable().trim();
        Map<String, String> unitLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);

        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            // Branch：诊疗目标取包装单位作为默认单位
            List<ActivityDefinition> definitions = activityDefinitionQueryService.findAllByIds(List.of(targetId));
            if (!definitions.isEmpty() && definitions.get(0) != null) {
                preferredUnitCode = definitions.get(0).getPackageUnitCode();
                if (StringUtils.isNotEmpty(preferredUnitCode)) {
                    String label = unitLabels.getOrDefault(preferredUnitCode.trim(), preferredUnitCode.trim());
                    options.add(new OptionDto(0L, preferredUnitCode.trim(), label));
                }
            }
        } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            // Branch：耗材目标汇总各计量单位码并去重
            DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(targetId);
            if (device != null) {
                Set<String> unitCodes = new LinkedHashSet<>();
                if (StringUtils.isNotEmpty(device.getPurchaseUnitCode())) {
                    unitCodes.add(device.getPurchaseUnitCode().trim());
                }
                if (StringUtils.isNotEmpty(device.getStockUnitCode())) {
                    unitCodes.add(device.getStockUnitCode().trim());
                }
                if (StringUtils.isNotEmpty(device.getPackageUnitCode())) {
                    unitCodes.add(device.getPackageUnitCode().trim());
                }
                if (StringUtils.isNotEmpty(device.getDoseUnitCode())) {
                    unitCodes.add(device.getDoseUnitCode().trim());
                }
                if (StringUtils.isNotEmpty(device.getSplitUnitCode())) {
                    unitCodes.add(device.getSplitUnitCode().trim());
                }
                for (String unitCode : unitCodes) {
                    if (StringUtils.isEmpty(preferredUnitCode)) {
                        preferredUnitCode = unitCode;
                    }
                    options.add(new OptionDto(0L, unitCode, unitLabels.getOrDefault(unitCode, unitCode)));
                }
            }
        }

        dto.setUnitCode(preferredUnitCode);
        dto.setUnitOptions(options);
        // Asm：返回默认单位与选项列表
        return dto;
    }

    /**
     * 保存前业务校验：绑定类型、来源、目标、数量与单位必填。
     *
     * @param request 保存请求
     */
    private void validateSaveRequest(SaveServiceDeviceBindingRequest request) {
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：请求体不能为空");
        }
        String typeCode = null;
        if (StringUtils.isNotEmpty(request.getBindType())) {
            ServiceDeviceTemplateTypeCode type =
                EnumUtil.fromCode(ServiceDeviceTemplateTypeCode.class, request.getBindType().trim());
            if (type != null) {
                typeCode = type.getCode();
            }
        }
        if (typeCode == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：绑定类型不能为空");
        }
        if (StringUtils.isEmpty(request.getRangeCode())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：适用范围不能为空");
        }
        ServiceDeviceTemplateRangeCode range =
            EnumUtil.fromCode(ServiceDeviceTemplateRangeCode.class, request.getRangeCode().trim());
        if (range == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：适用范围不合法");
        }
        if (ServiceDeviceTemplateRangeCode.HOSPITAL.equals(range)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT,
                "保存失败：请选择门诊或住院适用范围");
        }
        // 回写规范枚举 code，避免空白或别名落库
        request.setRangeCode(range.getCode());
        if (StringUtils.isEmpty(request.getSourceId())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：来源项不能为空");
        }
        if (StringUtils.isEmpty(request.getTargetTable())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：目标表不能为空");
        }
        if (StringUtils.isEmpty(request.getTargetId())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：目标ID不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：数量必须大于0");
        }
        if (StringUtils.isEmpty(request.getUnitCode())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：单位不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：状态不能为空");
        }
    }
}
