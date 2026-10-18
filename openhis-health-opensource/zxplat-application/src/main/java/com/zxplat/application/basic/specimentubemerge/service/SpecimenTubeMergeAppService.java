/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.basic.labdevice.dto.LabDeviceDto;
import com.zxplat.application.basic.labdevice.mapper.LabDeviceMapper;
import com.zxplat.application.basic.servicedevicebinding.assembler.ServiceDeviceBindingDisplayAssembler;
import com.zxplat.application.basic.servicedevicebinding.dto.ServiceDeviceBindingUnitOptionsDto;
import com.zxplat.application.basic.specimentubemerge.assembler.SpecimenTubeMergeDisplayAssembler;
import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeBindingDto;
import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeBindingsDto;
import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeConsumableLineDto;
import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeGroupDto;
import com.zxplat.application.basic.specimentubemerge.dto.SpecimenTubeMergeInitDto;
import com.zxplat.application.basic.specimentubemerge.request.GetBindingsRequest;
import com.zxplat.application.basic.specimentubemerge.request.ListConsumableOptionsRequest;
import com.zxplat.application.basic.specimentubemerge.request.PageDevicesRequest;
import com.zxplat.application.basic.specimentubemerge.request.SaveBindingsRequest;
import com.zxplat.application.basic.specimentubemerge.request.SearchActivitiesRequest;
import com.zxplat.application.basic.specimentubemerge.request.UnitOptionsByTargetRequest;
import com.zxplat.application.shared.converter.TextValueConverter;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.administration.labdevice.queries.PageLabDeviceQuery;
import com.zxplat.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.zxplat.domain.administration.labdevice.service.LabDeviceQueryService;
import com.zxplat.domain.custom.specimentubemerge.command.AlignSpecimenTubeMergeCommand;
import com.zxplat.domain.custom.specimentubemerge.command.AlignSpecimenTubeMergeLineCommand;
import com.zxplat.domain.custom.specimentubemerge.command.DeleteDeviceBindingsCommand;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.zxplat.domain.custom.specimentubemerge.service.SpecimenTubeMergeDomainService;
import com.zxplat.domain.custom.specimentubemerge.service.SpecimenTubeMergeQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.SpecimenTubeMergeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 基础数据「标本合管配置」应用服务（AS-BAS-CFG-001）。
 *
 * <p>编排 {@link SpecimenTubeMergeQueryService}、{@link SpecimenTubeMergeDomainService}
 * 与检验设备/诊疗/耗材读侧；接入方式：{@code POST /api/app/specimenTubeMerge/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecimenTubeMergeAppService {

    private final SpecimenTubeMergeQueryService specimenTubeMergeQueryService;
    private final SpecimenTubeMergeDomainService specimenTubeMergeDomainService;
    private final LabDeviceQueryService labDeviceQueryService;
    private final LabDeviceMapper labDeviceMapper;
    private final SpecimenTubeMergeDisplayAssembler specimenTubeMergeDisplayAssembler;
    private final ServiceDeviceBindingDisplayAssembler serviceDeviceBindingDisplayAssembler;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 写路径操作人门控（B.6.5），禁止 AppService 直读 ICurrentUser。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 返回合管配置页初始化数据。
     *
     * @return 初始化 DTO
     */
    public SpecimenTubeMergeInitDto init() {
        SpecimenTubeMergeInitDto dto = new SpecimenTubeMergeInitDto();
        // Pre：页面静态提示与单位/目标表下拉
        dto.setDeviceSearchHint("输入设备名称/编码检索");
        dto.setLabActivityCategoryCode(SpecimenTubeMergeConstants.LAB_ACTIVITY_CATEGORY_CODE);
        dto.setUnitOptions(terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE));
        dto.setTargetTableOptions(this.buildTargetTableOptions());
        // Asm：返回初始化 DTO
        return dto;
    }

    /**
     * 左栏分页检索启用中的检验设备。
     *
     * @param request 分页请求
     * @return 设备分页 DTO
     */
    public PagedResult<LabDeviceDto> pageDevices(PageDevicesRequest request) {
        PageDevicesRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new PageDevicesRequest();
        }
        // Gate：归一化分页窗口
        int pageSize = 50;
        if (safeRequest.getPageSize() != null && safeRequest.getPageSize() > 0) {
            pageSize = safeRequest.getPageSize();
        }
        // Pre：仅查启用态设备，供合管配置左栏
        PageLabDeviceQuery query = new PageLabDeviceQuery();
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        query.setCategoryCode(trimToNull(safeRequest.getCategoryCode()));
        // Cmd：分页读检验设备
        Page<LabDeviceDto> page = labDeviceQueryService.pageLabDevice(
            query,
            PageRequest.of(safeRequest.springPageIndex(), pageSize))
            .map(labDeviceMapper::toDto);
        // Asm：统一分页响应
        return PagedResult.fromPage(page);
    }

    /**
     * 查询设备下合管绑定详情。
     *
     * @param request 含 deviceId 的请求
     * @return 绑定详情 DTO
     */
    public SpecimenTubeMergeBindingsDto getBindings(GetBindingsRequest request) {
        String deviceIdRaw = null;
        if (request != null) {
            deviceIdRaw = request.getDeviceId();
        }
        // Gate：字符串主键须可解析为 Long
        Long deviceId = TextValueConverter.requireLong(deviceIdRaw, "查询合管绑定失败：deviceId 不能为空");
        return this.getBindings(deviceId);
    }

    /**
     * 查询设备下合管绑定详情（含多组）。
     *
     * @param deviceId 设备主键
     * @return 绑定详情 DTO
     */
    public SpecimenTubeMergeBindingsDto getBindings(Long deviceId) {
        // Gate：设备主键不能为空
        if (deviceId == null) {
            throw new ValidationException("查询合管绑定失败：deviceId 不能为空");
        }
        // Pre：校验设备存在并加载绑定读模型
        LabDeviceDetailQueryModel device = labDeviceQueryService.getLabDevice(deviceId);
        if (device == null) {
            throw new EntityNotFoundException("设备不存在或已删除");
        }
        Long bloodFeeActivityId = specimenTubeMergeDisplayAssembler.resolveBloodCollectionFeeActivityId();
        List<SpecimenTubeMergeLineQueryModel> models = specimenTubeMergeQueryService.listByDeviceId(deviceId);

        SpecimenTubeMergeBindingsDto dto = new SpecimenTubeMergeBindingsDto();
        dto.setDeviceId(deviceId);
        dto.setDeviceName(device.getName());
        dto.setDeviceBusNo(device.getBusNo());
        // Asm：按 groupNo 拆组并投影活动/耗材/采血费
        dto.setGroups(this.buildGroupDtos(models, bloodFeeActivityId));
        return dto;
    }

    /**
     * 全量保存设备合管绑定（可含多组；缺席旧组整组软删）。
     *
     * @param request 保存请求
     */
    public void saveBindings(SaveBindingsRequest request) {
        // Gate：请求体、重复项与采血费口径校验
        validateSaveRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
            "请先登录后再保存合管配置");
        Long deviceId = TextValueConverter.requireLong(request.getDeviceId(), "保存合管配置失败：deviceId 不能为空");
        // Pre：组装全量对齐命令（设备下全部组）
        AlignSpecimenTubeMergeCommand command = new AlignSpecimenTubeMergeCommand();
        command.setDeviceId(deviceId);
        command.setOperatorId(operatorId);
        List<AlignSpecimenTubeMergeLineCommand> lines = new ArrayList<>();
        Long bloodFeeActivityId = null;
        boolean anyBindBloodFee = false;
        List<SaveBindingsRequest.GroupBindingsRequest> groups = request.getGroups();
        if (groups == null) {
            groups = new ArrayList<>();
        }
        int assignedGroupNo = SpecimenTubeMergeConstants.MIN_GROUP_NO;
        for (SaveBindingsRequest.GroupBindingsRequest group : groups) {
            if (group == null) {
                continue;
            }
            Integer groupNo = group.getGroupNo();
            // 未传组号时按提交顺序从 1 起编号
            if (groupNo == null || groupNo < SpecimenTubeMergeConstants.MIN_GROUP_NO) {
                groupNo = assignedGroupNo;
            }
            assignedGroupNo = groupNo + 1;
            int displayOrder = 0;
            // Pre：检验项目 activity 行（不含采血费）
            if (group.getActivityIds() != null) {
                for (String activityIdRaw : group.getActivityIds()) {
                    Long activityId = TextValueConverter.requireLong(activityIdRaw, "检验项目 id 不合法");
                    if (activityId == null) {
                        continue;
                    }
                    AlignSpecimenTubeMergeLineCommand line = new AlignSpecimenTubeMergeLineCommand();
                    line.setGroupNo(groupNo);
                    line.setTargetTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
                    line.setTargetId(activityId);
                    line.setDisplayOrder(displayOrder);
                    displayOrder = displayOrder + 1;
                    lines.add(line);
                }
            }
            // Pre：本组勾选采血费时追加采血费 activity 行（可跨组复用）
            if (Boolean.TRUE.equals(group.getBindBloodCollectionFee())) {
                anyBindBloodFee = true;
                if (bloodFeeActivityId == null) {
                    bloodFeeActivityId = this.requireBloodCollectionFeeActivityId();
                }
                AlignSpecimenTubeMergeLineCommand line = new AlignSpecimenTubeMergeLineCommand();
                line.setGroupNo(groupNo);
                line.setTargetTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
                line.setTargetId(bloodFeeActivityId);
                line.setDisplayOrder(displayOrder);
                displayOrder = displayOrder + 1;
                lines.add(line);
            }
            // Pre：本组耗材定义行（数量+单位）
            if (group.getConsumableLines() != null) {
                for (SaveBindingsRequest.ConsumableLineRequest consumableLine : group.getConsumableLines()) {
                    if (consumableLine == null || StringUtils.isEmpty(consumableLine.getTargetId())) {
                        continue;
                    }
                    Long targetId = TextValueConverter.requireLong(consumableLine.getTargetId(), "耗材 id 不合法");
                    AlignSpecimenTubeMergeLineCommand line = new AlignSpecimenTubeMergeLineCommand();
                    line.setGroupNo(groupNo);
                    line.setTargetTable(TableNameConstants.ADM_DEVICE_DEFINITION);
                    line.setTargetId(targetId);
                    line.setQuantity(consumableLine.getQuantity());
                    line.setUnitCode(consumableLine.getUnitCode());
                    if (consumableLine.getDisplayOrder() != null) {
                        line.setDisplayOrder(consumableLine.getDisplayOrder());
                    } else {
                        line.setDisplayOrder(displayOrder);
                        displayOrder = displayOrder + 1;
                    }
                    lines.add(line);
                }
            }
        }
        // Pre：告知领域层采血费主键，写侧跳过其跨设备唯一校验
        if (anyBindBloodFee) {
            command.setBloodCollectionFeeActivityId(bloodFeeActivityId);
        }
        command.setLines(lines);
        // Cmd：领域层全量对齐（缺席旧行软删）
        specimenTubeMergeDomainService.alignBindingsForDevice(command);
        log.info("Saved specimen tube merge bindings for device: {}, groupCount={}", deviceId, groups.size());
    }

    /**
     * 删除设备下全部合管绑定。
     *
     * @param deviceId 设备主键
     */
    public void deleteDeviceBindings(Long deviceId) {
        // Gate：设备主键与登录操作人
        if (deviceId == null) {
            throw new ValidationException("删除合管绑定失败：deviceId 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
            "请先登录后再删除合管配置");
        // Cmd：逻辑删除设备下全部绑定行
        DeleteDeviceBindingsCommand command = new DeleteDeviceBindingsCommand();
        command.setDeviceId(deviceId);
        command.setOperatorId(operatorId);
        specimenTubeMergeDomainService.deleteDeviceBindings(command);
        log.info("Deleted specimen tube merge bindings for device: {}", deviceId);
    }

    /**
     * 按绑定目标解析单位下拉。
     *
     * @param request 目标表与目标 id
     * @return 单位选项 DTO
     */
    public ServiceDeviceBindingUnitOptionsDto unitOptionsByTarget(UnitOptionsByTargetRequest request) {
        // Gate：目标表与主键必填
        if (request == null || StringUtils.isEmpty(request.getTargetTable())) {
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "目标表不能为空");
        }
        Long targetId = TextValueConverter.requireLong(request.getTargetId(), "目标ID不能为空");
        ServiceDeviceBindingUnitOptionsDto dto = new ServiceDeviceBindingUnitOptionsDto();
        List<OptionDto> options = new ArrayList<>();
        String preferredUnitCode = null;
        String table = request.getTargetTable().trim();
        Map<String, String> unitLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);

        // Pre：诊疗 activity 仅包装单位可选
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            List<ActivityDefinition> definitions = activityDefinitionQueryService.findAllByIds(List.of(targetId));
            if (!definitions.isEmpty() && definitions.get(0) != null) {
                preferredUnitCode = definitions.get(0).getPackageUnitCode();
                if (StringUtils.isNotEmpty(preferredUnitCode)) {
                    String label = unitLabels.getOrDefault(preferredUnitCode.trim(), preferredUnitCode.trim());
                    options.add(new OptionDto(0L, preferredUnitCode.trim(), label));
                }
            }
        // Pre：耗材定义取采购/库存/包装等单位并集
        } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
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
        // Gate：未知目标表拒绝解析
        } else {
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "目标表不合法");
        }
        // Asm：返回首选单位与下拉
        dto.setUnitCode(preferredUnitCode);
        dto.setUnitOptions(options);
        return dto;
    }

    /**
     * 检索检验类诊疗项目（目录类别 22）。
     *
     * @param request 检索条件
     * @return 下拉选项
     */
    public List<OptionDto> searchActivities(SearchActivitiesRequest request) {
        SearchActivitiesRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new SearchActivitiesRequest();
        }
        // Gate：限制检索条数，避免下拉一次拉全库
        int limit = SpecimenTubeMergeConstants.DEFAULT_ACTIVITY_SEARCH_LIMIT;
        if (safeRequest.getLimit() != null && safeRequest.getLimit() > 0) {
            limit = safeRequest.getLimit();
        }
        // Pre：仅检验类目录（category 22）且启用态
        ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
        query.setCategoryCode(SpecimenTubeMergeConstants.LAB_ACTIVITY_CATEGORY_CODE);
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        // Cmd：分页读诊疗目录
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
            query,
            PageRequest.of(0, limit));
        // Asm：投影为下拉 OptionDto
        List<OptionDto> options = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel item : page.getContent()) {
            if (item.getId() == null) {
                continue;
            }
            String label = item.getName();
            if (StringUtils.isNotEmpty(item.getBusNo())) {
                label = item.getBusNo() + " " + item.getName();
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), label));
        }
        return options;
    }

    /**
     * 耗材定义下拉（实时读库，支持关键字过滤）。
     *
     * @param request 可选关键字
     * @return 耗材下拉项
     */
    public List<OptionDto> listConsumableOptions(ListConsumableOptionsRequest request) {
        String keyword = null;
        if (request != null) {
            keyword = request.getKeyword();
        }
        // Cmd：复用服务设备绑定页的耗材候选 enrich
        return serviceDeviceBindingDisplayAssembler.buildDeviceDefOptions(keyword);
    }

    /**
     * 配置行按 groupNo 组装前端 groups 契约。
     *
     * @param models             设备下全部绑定行
     * @param bloodFeeActivityId 采血费 activity 主键；可空
     * @return 按组序号升序的组 DTO；无行时返回空组列表
     */
    private List<SpecimenTubeMergeGroupDto> buildGroupDtos(
        List<SpecimenTubeMergeLineQueryModel> models,
        Long bloodFeeActivityId
    ) {
        Map<Integer, List<SpecimenTubeMergeLineQueryModel>> byGroup = new TreeMap<>();
        if (models != null) {
            for (SpecimenTubeMergeLineQueryModel model : models) {
                if (model == null) {
                    continue;
                }
                Integer groupNo = model.getGroupNo();
                if (groupNo == null || groupNo < SpecimenTubeMergeConstants.MIN_GROUP_NO) {
                    groupNo = SpecimenTubeMergeConstants.DEFAULT_GROUP_NO;
                }
                List<SpecimenTubeMergeLineQueryModel> bucket = byGroup.get(groupNo);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byGroup.put(groupNo, bucket);
                }
                bucket.add(model);
            }
        }
        List<SpecimenTubeMergeGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<SpecimenTubeMergeLineQueryModel>> entry : byGroup.entrySet()) {
            Integer groupNo = entry.getKey();
            List<List<SpecimenTubeMergeBindingDto>> split =
                specimenTubeMergeDisplayAssembler.splitBindingDtos(entry.getValue(), bloodFeeActivityId);
            SpecimenTubeMergeGroupDto groupDto = new SpecimenTubeMergeGroupDto();
            groupDto.setGroupNo(groupNo);
            groupDto.setGroupName("组" + groupNo);
            List<String> activityIds = new ArrayList<>();
            for (SpecimenTubeMergeBindingDto binding : split.get(0)) {
                if (binding != null && binding.getTargetId() != null) {
                    activityIds.add(String.valueOf(binding.getTargetId()));
                }
            }
            groupDto.setActivityIds(activityIds);
            List<SpecimenTubeMergeConsumableLineDto> consumableLines = new ArrayList<>();
            for (SpecimenTubeMergeBindingDto binding : split.get(1)) {
                if (binding == null) {
                    continue;
                }
                SpecimenTubeMergeConsumableLineDto line = new SpecimenTubeMergeConsumableLineDto();
                line.setId(binding.getId());
                line.setTargetId(binding.getTargetId());
                line.setTargetName(binding.getTargetName());
                line.setQuantity(binding.getQuantity());
                line.setUnitCode(binding.getUnitCode());
                line.setUnitName(binding.getUnitCodeText());
                line.setDisplayOrder(binding.getDisplayOrder());
                consumableLines.add(line);
            }
            groupDto.setConsumableLines(consumableLines);
            boolean bindBloodFee = false;
            if (bloodFeeActivityId != null) {
                for (SpecimenTubeMergeLineQueryModel model : entry.getValue()) {
                    if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(model.getTargetTable())
                        && bloodFeeActivityId.equals(model.getTargetId())) {
                        bindBloodFee = true;
                        break;
                    }
                }
            }
            groupDto.setBindBloodCollectionFee(bindBloodFee);
            groups.add(groupDto);
        }
        return groups;
    }

    /**
     * 保存请求门禁：deviceId、跨组检验项目重复、组内耗材重复、采血费口径。
     *
     * @param request 保存请求
     */
    private void validateSaveRequest(SaveBindingsRequest request) {
        if (request == null || StringUtils.isEmpty(request.getDeviceId())) {
            throw new ValidationException("保存合管配置失败：deviceId 不能为空");
        }
        List<SaveBindingsRequest.GroupBindingsRequest> groups = request.getGroups();
        if (groups == null) {
            return;
        }
        Set<String> labActivityAcrossGroups = new LinkedHashSet<>();
        Long bloodFeeId = specimenTubeMergeDisplayAssembler.resolveBloodCollectionFeeActivityId();
        for (SaveBindingsRequest.GroupBindingsRequest group : groups) {
            if (group == null) {
                continue;
            }
            if (group.getActivityIds() != null) {
                Set<String> dedupeInGroup = new LinkedHashSet<>();
                for (String activityIdRaw : group.getActivityIds()) {
                    if (StringUtils.isEmpty(activityIdRaw)) {
                        continue;
                    }
                    String trimmed = activityIdRaw.trim();
                    if (!dedupeInGroup.add(trimmed)) {
                        throw new ValidationException("保存合管配置失败：同一合管组内检验项目重复");
                    }
                    // 跨组检验项目不可重复
                    if (!labActivityAcrossGroups.add(trimmed)) {
                        throw new ValidationException("保存合管配置失败：不同合管组不能配置相同检验项目");
                    }
                    if (bloodFeeId != null) {
                        Long activityId = TextValueConverter.requireLong(trimmed, "检验项目 id 不合法");
                        if (Objects.equals(activityId, bloodFeeId)) {
                            throw new ValidationException("保存合管配置失败：采血费请通过 bindBloodCollectionFee 勾选");
                        }
                    }
                }
            }
            if (group.getConsumableLines() != null) {
                Set<String> dedupe = new LinkedHashSet<>();
                for (SaveBindingsRequest.ConsumableLineRequest line : group.getConsumableLines()) {
                    if (line == null || StringUtils.isEmpty(line.getTargetId())) {
                        continue;
                    }
                    if (!dedupe.add(line.getTargetId().trim())) {
                        throw new ValidationException("保存合管配置失败：同一合管组内耗材重复");
                    }
                    if (line.getQuantity() == null || line.getQuantity().signum() <= 0) {
                        throw new ValidationException("保存合管配置失败：耗材数量须大于 0");
                    }
                    if (StringUtils.isEmpty(line.getUnitCode())) {
                        throw new ValidationException("保存合管配置失败：耗材单位不能为空");
                    }
                }
            }
        }
    }

    /**
     * 保存时解析采血费 activity 主键；未配置或名称不唯一时抛可读业务异常。
     *
     * @return 采血费 activity 主键
     */
    private Long requireBloodCollectionFeeActivityId() {
        String feeName = SpecimenTubeMergeConstants.BLOOD_COLLECTION_FEE_ACTIVITY_NAME;
        try {
            return activityDefinitionQueryService.requireIdByExactName(feeName);
        } catch (BusinessException ex) {
            String detail = ex.getMessage();
            // 名称不唯一：提示合并/改名，避免笼统「未配置」
            if (detail != null && detail.contains("不唯一")) {
                throw new BusinessException(
                    ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
                    "勾选采血费失败：诊疗目录中名称「" + feeName + "」不唯一，请合并或改名后再保存");
            }
            // 未找到或其它业务失败：引导维护精确名称
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
                "勾选采血费失败：诊疗目录中未找到名称精确为「" + feeName + "」的项目，请先维护后再勾选");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
                "勾选采血费失败：无法解析诊疗项目「" + feeName + "」，请检查诊疗目录后重试");
        }
    }

    /**
     * 合管绑定目标表下拉（检验项目 / 耗材定义）。
     *
     * @return 目标表 OptionDto 列表
     */
    private List<OptionDto> buildTargetTableOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(1L, TableNameConstants.WKF_ACTIVITY_DEFINITION, "检验项目"));
        options.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return options;
    }
}
