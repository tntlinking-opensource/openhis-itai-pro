/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.frequency.service;

import com.openhis.health.application.basic.frequency.dto.ComputeFrequencyExecutionTimePointsResponse;
import com.openhis.health.application.basic.frequency.dto.FrequencyDto;
import com.openhis.health.application.basic.frequency.dto.FrequencyInitDto;
import com.openhis.health.application.basic.frequency.mapper.FrequencyMapper;
import com.openhis.health.application.basic.frequency.request.ComputeFrequencyExecutionTimePointsRequest;
import com.openhis.health.application.basic.frequency.request.CreateFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.QueryFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.UpdateFrequencyRequest;
import com.openhis.health.domain.custom.frequency.command.ActivateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.DeactivateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.DeleteFrequencyCommand;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyPageItemQueryModel;
import com.openhis.health.domain.share.enums.FrequencyAllowCrossDay;
import com.openhis.health.domain.share.enums.FrequencyCycleType;
import com.openhis.health.domain.custom.frequency.service.FrequencyDomainService;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础数据「用药频次」应用服务：分页查询、增删改及启停，编排 {@link FrequencyQueryService} 与 {@link FrequencyDomainService}。
 *
 * <p>分页关键字同时接受名称、编码与通用 {@code keyword}，由 {@link #normalizeKeyword(QueryFrequencyRequest)} 归一；
 * 部分展示字段在出参前由 {@link #enrichFrequencyFields(Frequency)} 等补齐默认值，避免前端分端差异。</p>
 */
@Service
@RequiredArgsConstructor
public class FrequencyAppService {

    /** 频次读侧：分页与按 id 加载。 */
    private final FrequencyQueryService frequencyQueryService;
    /** 频次写侧：创建、修改、启停与删除。 */
    private final FrequencyDomainService frequencyDomainService;
    /** 请求/实体与 DTO、领域命令映射。 */
    private final FrequencyMapper frequencyMapper;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 返回频次维护表单所需的下拉选项（状态、周期类型、场景、跨天）。
     *
     * @return 初始化 DTO，各选项列表非 {@code null}
     */
    public FrequencyInitDto init() {
        // Gate：只读页初始化，不写库
        // Pre：装配状态、周期、场景、跨天与周期单位下拉
        // Asm：映射页初始化响应
        FrequencyInitDto dto = new FrequencyInitDto();
        dto.setStatusOptions(List.of(
            new OptionDto(1L, "1", "启用"),
            new OptionDto(2L, "0", "停用")
        ));
        dto.setCycleTypeOptions(List.of(
            new OptionDto(FrequencyCycleType.DAILY.getSortOrder(), FrequencyCycleType.DAILY.getCode(), FrequencyCycleType.DAILY.getDisplay()),
            new OptionDto(FrequencyCycleType.WEEKLY.getSortOrder(), FrequencyCycleType.WEEKLY.getCode(), FrequencyCycleType.WEEKLY.getDisplay()),
            new OptionDto(FrequencyCycleType.INTERVAL.getSortOrder(), FrequencyCycleType.INTERVAL.getCode(), FrequencyCycleType.INTERVAL.getDisplay()),
            new OptionDto(FrequencyCycleType.AS_NEEDED.getSortOrder(), FrequencyCycleType.AS_NEEDED.getCode(), FrequencyCycleType.AS_NEEDED.getDisplay()),
            new OptionDto(FrequencyCycleType.FIXED_COUNT.getSortOrder(), FrequencyCycleType.FIXED_COUNT.getCode(), FrequencyCycleType.FIXED_COUNT.getDisplay())
        ));
        dto.setSceneOptions(List.of(
            new OptionDto(1L, "outpatient", "门诊"),
            new OptionDto(2L, "inpatient", "住院"),
            new OptionDto(3L, "nursing", "护理"),
            new OptionDto(4L, "all", "全部")
        ));
        dto.setAllowCrossDayOptions(List.of(
            new OptionDto(FrequencyAllowCrossDay.SAME_DAY.getSortOrder(), FrequencyAllowCrossDay.SAME_DAY.getCode(), FrequencyAllowCrossDay.SAME_DAY.getDisplay()),
            new OptionDto(FrequencyAllowCrossDay.CROSS_DAY.getSortOrder(), FrequencyAllowCrossDay.CROSS_DAY.getCode(), FrequencyAllowCrossDay.CROSS_DAY.getDisplay()),
            new OptionDto(FrequencyAllowCrossDay.BY_SHIFT.getSortOrder(), FrequencyAllowCrossDay.BY_SHIFT.getCode(), FrequencyAllowCrossDay.BY_SHIFT.getDisplay())
        ));
        dto.setPeriodUnitOptions(List.of(
            new OptionDto(1L, CustomConstants.PERIOD_UNIT_MINUTE, "分钟"),
            new OptionDto(2L, CustomConstants.PERIOD_UNIT_HOUR, "小时"),
            new OptionDto(3L, CustomConstants.PERIOD_UNIT_DAY, "天"),
            new OptionDto(4L, CustomConstants.PERIOD_UNIT_WEEK, "周"),
            new OptionDto(5L, CustomConstants.PERIOD_UNIT_MONTH, "月")
        ));
        return dto;
    }

    /**
     * 分页查询频次列表，关键字同时匹配名称或编码（归一后下推到查询服务）。
     *
     * @param request 分页与筛选条件；{@code null} 时使用默认第 1 页、每页 50 条
     * @return 分页 DTO 结果
     */
    public PagedResult<FrequencyDto> page(QueryFrequencyRequest request) {
        // Gate：归一分页请求，避免 NPE
        QueryFrequencyRequest query = ensureQueryRequest(request);
        // Pre：归一关键字并下推分页查询
        int pageIndex = query.springPageIndex();
        int pageSize;
        if (query.getPageSize() == null || query.getPageSize() <= 0) {
            pageSize = 50;
        } else {
            pageSize = query.getPageSize();
        }
        String keyword = normalizeKeyword(query);

        Page<FrequencyPageItemQueryModel> page = frequencyQueryService.pageFrequencies(keyword, pageIndex, pageSize)
            .map(this::enrichPageItemFields);
        // Asm：映射分页 DTO
        return PagedResult.fromPage(page.map(frequencyMapper::toDto));
    }

    /**
     * 计算频次在给定时间窗内的计划执行次数与时点（纯读侧，不写库）。
     *
     * @param request 频次编码与时间窗
     * @return 执行次数与升序时点列表
     */
    public ComputeFrequencyExecutionTimePointsResponse computeExecutionTimePoints(
        ComputeFrequencyExecutionTimePointsRequest request) {
        // Gate：频次编码与时间窗必填
        if (request == null || StringUtils.isEmpty(request.getRateCode())) {
            throw new ValidationException("计算执行次数失败：频次编码不能为空");
        }
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();
        if (startTime == null || endTime == null) {
            throw new ValidationException("计算执行次数失败：开始/结束时间不能为空");
        }
        // Pre：纯读侧计算计划执行时点
        List<FrequencyExecutionTimePointQueryModel> points = frequencyQueryService.computeExecutionTimePoints(
            request.getRateCode().trim(),
            startTime,
            endTime);
        ComputeFrequencyExecutionTimePointsResponse response = new ComputeFrequencyExecutionTimePointsResponse();
        response.setExecutionCount(points.size());
        List<String> executionAtList = new ArrayList<>();
        for (FrequencyExecutionTimePointQueryModel point : points) {
            if (point.getExecutionAt() != null) {
                executionAtList.add(point.getExecutionAt().toString());
            }
        }
        response.setExecutionAtList(executionAtList);
        // Asm：映射计算结果响应
        return response;
    }

    /**
     * 新增频次：校验后创建领域实体并回读装配为 DTO。
     *
     * @param request 创建请求
     * @return 新建频次展示对象
     * @throws ValidationException 参数不合法时
     */
    public FrequencyDto create(CreateFrequencyRequest request) {
        // Gate：创建请求校验与写路径操作人
        validateCreateRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "请先登录后再新增频次");
        // Cmd：创建频次
        Long id = frequencyDomainService.createFrequency(
            frequencyMapper.toCreateCommand(request, operatorId));
        // Pre：回读新建实体
        Frequency created = frequencyQueryService.findById(id);
        // Asm：映射展示 DTO
        return frequencyMapper.toDto(enrichFrequencyFields(created));
    }

    /**
     * 更新频次：校验后下发修改命令并回读装配。
     *
     * @param request 编辑请求
     * @return 更新后展示对象
     * @throws ValidationException 参数不合法时
     */
    public FrequencyDto update(UpdateFrequencyRequest request) {
        // Gate：更新请求校验与写路径操作人
        validateUpdateRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "请先登录后再更新频次");
        // Cmd：修改频次
        frequencyDomainService.modifyFrequency(
            frequencyMapper.toModifyCommand(request, operatorId));
        // Pre：回读更新后实体
        Frequency updated = frequencyQueryService.findById(request.getId());
        // Asm：映射展示 DTO
        return frequencyMapper.toDto(enrichFrequencyFields(updated));
    }

    /**
     * 删除频次（逻辑删除语义由领域/底座实现）。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void delete(Long id) {
        // Gate：主键非空且须已登录操作人
        if (id == null) {
            throw new ValidationException("删除频次失败：id 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "请先登录后再删除频次");
        DeleteFrequencyCommand command = new DeleteFrequencyCommand();
        command.setId(id);
        command.setOperatorId(operatorId);
        // Cmd：逻辑删除频次
        frequencyDomainService.deleteFrequency(command);
    }

    /**
     * 启用频次。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void enable(Long id) {
        // Gate：主键非空且须已登录操作人
        if (id == null) {
            throw new ValidationException("启用频次失败：id 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "请先登录后再启用频次");
        ActivateFrequencyCommand command = new ActivateFrequencyCommand();
        command.setId(id);
        command.setOperatorId(operatorId);
        // Cmd：启用频次
        frequencyDomainService.activateFrequency(command);
    }

    /**
     * 停用频次。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void disable(Long id) {
        // Gate：主键非空且须已登录操作人
        if (id == null) {
            throw new ValidationException("停用频次失败：id 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "请先登录后再停用频次");
        DeactivateFrequencyCommand command = new DeactivateFrequencyCommand();
        command.setId(id);
        command.setOperatorId(operatorId);
        // Cmd：停用频次
        frequencyDomainService.deactivateFrequency(command);
    }

    /**
     * 为 null 请求补默认分页参数，避免 NPE 与无界查询。
     *
     * @param request 原始请求，可为 {@code null}
     * @return 非 {@code null} 的请求副本或默认实例
     */
    private QueryFrequencyRequest ensureQueryRequest(QueryFrequencyRequest request) {
        if (request != null) {
            return request;
        }
        QueryFrequencyRequest query = new QueryFrequencyRequest();
        query.setPageNo(1);
        query.setPageSize(50);
        return query;
    }

    /**
     * 将名称、编码、通用 keyword 归一为单一关键字串；优先级：name → rateCode → keyword。
     *
     * @param request 非 {@code null} 的查询请求（调用方已 ensure）
     * @return trim 后的关键字，可能为空串表示不按关键字过滤
     */
    private String normalizeKeyword(QueryFrequencyRequest request) {
        // 优先按名称模糊，兼容旧版仅传 name 的查询表单
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        // 次之按频次编码精确/模糊
        if (request.getRateCode() != null && !request.getRateCode().isBlank()) {
            return request.getRateCode().trim();
        }
        // 最后回落通用 keyword 字段
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            return request.getKeyword().trim();
        }
        // 无检索条件：由域查询服务按「不过滤关键字」处理
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /**
     * 创建路径必填项校验：名称、编码、周期类型。
     *
     * @param request 创建请求
     * @throws ValidationException 不满足时
     */
    private void validateCreateRequest(CreateFrequencyRequest request) {
        if (request == null) {
            throw new ValidationException("新增频次失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("新增频次失败：频次名称不能为空");
        }
        if (request.getRateCode() == null || request.getRateCode().isBlank()) {
            throw new ValidationException("新增频次失败：频次编码不能为空");
        }
        if (request.getCycleType() == null || request.getCycleType().isBlank()) {
            throw new ValidationException("新增频次失败：周期类型不能为空");
        }
    }

    /**
     * 更新路径校验：主键与名称必填（编码等不可变项由领域保证）。
     *
     * @param request 编辑请求
     * @throws ValidationException 不满足时
     */
    private void validateUpdateRequest(UpdateFrequencyRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新频次失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("更新频次失败：频次名称不能为空");
        }
    }

    /**
     * 详情/编辑回显前补齐领域可能为 null 的展示字段，避免前端分支过多。
     *
     * @param frequency 领域实体
     * @return 同一引用，便于链式调用；{@code null} 时返回 {@code null}
     */
    private Frequency enrichFrequencyFields(Frequency frequency) {
        if (frequency == null) {
            return null;
        }
        // 统一默认值，保证前端列表/编辑回显稳定。
        if (frequency.getCycleType() == null || frequency.getCycleType().isBlank()) {
            frequency.setCycleType(FrequencyCycleType.DAILY.getCode());
        }
        if (frequency.getAllowCrossDay() == null || frequency.getAllowCrossDay().isBlank()) {
            frequency.setAllowCrossDay(FrequencyAllowCrossDay.SAME_DAY.getCode());
        }
        return frequency;
    }

    /**
     * 列表行在映射为 DTO 前补齐默认周期类型等，与详情路径展示口径对齐。
     *
     * @param model 分页行读模型
     * @return 同一引用；{@code null} 时返回 {@code null}
     */
    private FrequencyPageItemQueryModel enrichPageItemFields(FrequencyPageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        if (model.getCycleType() == null || model.getCycleType().isBlank()) {
            model.setCycleType(FrequencyCycleType.DAILY.getCode());
        }
        return model;
    }
}
