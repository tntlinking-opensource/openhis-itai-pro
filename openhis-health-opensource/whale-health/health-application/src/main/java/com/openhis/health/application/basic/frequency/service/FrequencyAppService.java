/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.frequency.service;

import com.openhis.health.application.common.converter.TextValueConverter;
import com.openhis.health.application.basic.frequency.dto.FrequencyDto;
import com.openhis.health.application.basic.frequency.dto.FrequencyInitDto;
import com.openhis.health.application.basic.frequency.mapper.FrequencyMapper;
import com.openhis.health.application.basic.frequency.request.CreateFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.IdValueRequest;
import com.openhis.health.application.basic.frequency.request.QueryFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.UpdateFrequencyRequest;
import com.openhis.health.domain.custom.frequency.command.ActivateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.DeactivateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.DeleteFrequencyCommand;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyPageItemQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyDomainService;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

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

    /**
     * 返回频次维护表单所需的下拉选项（状态、周期类型、场景、跨天）。
     *
     * @return 初始化 DTO，各选项列表非 {@code null}
     */
    public FrequencyInitDto init() {
        FrequencyInitDto dto = new FrequencyInitDto();
        dto.setStatusOptions(List.of(
            new OptionDto(1L, "1", "启用"),
            new OptionDto(2L, "0", "停用")
        ));
        dto.setCycleTypeOptions(List.of(
            new OptionDto(1L, "1", "按天"),
            new OptionDto(2L, "2", "按周"),
            new OptionDto(3L, "3", "按月"),
            new OptionDto(4L, "4", "按小时间隔"),
            new OptionDto(5L, "5", "按需(PRN)")
        ));
        dto.setSceneOptions(List.of(
            new OptionDto(1L, "1", "门诊"),
            new OptionDto(2L, "2", "住院"),
            new OptionDto(3L, "3", "护理"),
            new OptionDto(4L, "4", "通用")
        ));
        dto.setAllowCrossDayOptions(List.of(
            new OptionDto(1L, "0", "不允许"),
            new OptionDto(2L, "1", "允许")
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
        QueryFrequencyRequest query = ensureQueryRequest(request);
        // Spring Data 页下标 0-based：AbstractPageQuery#springPageIndex()
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
        return PagedResult.fromPage(page.map(frequencyMapper::toDto));
    }

    /**
     * 新增频次：校验后创建领域实体并回读装配为 DTO。
     *
     * @param request 创建请求
     * @return 新建频次展示对象
     * @throws ValidationException 参数不合法时
     */
    public FrequencyDto create(CreateFrequencyRequest request) {
        validateCreateRequest(request);
        Long id = frequencyDomainService.createFrequency(frequencyMapper.toCreateCommand(request));
        Frequency created = frequencyQueryService.findById(id);
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
        validateUpdateRequest(request);
        frequencyDomainService.modifyFrequency(frequencyMapper.toModifyCommand(request));
        Frequency updated = frequencyQueryService.findById(request.getId());
        return frequencyMapper.toDto(enrichFrequencyFields(updated));
    }

    /**
     * 删除频次（逻辑删除语义由领域/底座实现）。
     *
     * @param id 主键字符串
     * @throws ValidationException id 非法时
     */
    public void delete(String id) {
        Long parsedId = TextValueConverter.requireLong(id, "删除频次失败：id 非法");
        DeleteFrequencyCommand command = new DeleteFrequencyCommand();
        command.setId(parsedId);
        command.setOperatorId(0L);
        frequencyDomainService.deleteFrequency(command);
    }

    /**
     * 启用频次。
     *
     * @param request 含字符串主键的请求体
     * @throws ValidationException id 非法时
     */
    public void enable(IdValueRequest request) {
        String enableIdValue = null;
        if (request != null) {
            enableIdValue = request.getValue();
        }
        Long id = TextValueConverter.requireLong(enableIdValue, "启用频次失败：id 非法");
        ActivateFrequencyCommand command = new ActivateFrequencyCommand();
        command.setId(id);
        command.setOperatorId(0L);
        frequencyDomainService.activateFrequency(command);
    }

    /**
     * 停用频次。
     *
     * @param request 含字符串主键的请求体
     * @throws ValidationException id 非法时
     */
    public void disable(IdValueRequest request) {
        String disableIdValue = null;
        if (request != null) {
            disableIdValue = request.getValue();
        }
        Long id = TextValueConverter.requireLong(disableIdValue, "停用频次失败：id 非法");
        DeactivateFrequencyCommand command = new DeactivateFrequencyCommand();
        command.setId(id);
        command.setOperatorId(0L);
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
        if (request.getCycleType() == null) {
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
        if (frequency.getCycleType() == null) {
            frequency.setCycleType(1);
        }
        if (frequency.getAllowCrossDay() == null) {
            frequency.setAllowCrossDay(0);
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
        if (model.getCycleType() == null) {
            model.setCycleType(1);
        }
        return model;
    }
}
