/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.body.service;

import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.application.basic.body.dto.BodyDto;
import com.zxplat.application.basic.body.mapper.BodyMapper;
import com.zxplat.application.basic.body.request.CreateBodyRequest;
import com.zxplat.application.basic.body.request.UpdateBodyRequest;
import com.zxplat.domain.diagnosis.bodystructure.command.ActiveBodyStructureCommand;
import com.zxplat.domain.diagnosis.bodystructure.command.DeleteBodyStructureCommand;
import com.zxplat.domain.diagnosis.bodystructure.command.InactiveBodyStructureCommand;
import com.zxplat.domain.diagnosis.bodystructure.queries.model.BodyStructureInfoQueryModel;
import com.zxplat.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.zxplat.domain.diagnosis.bodystructure.service.BodyStructureDomainService;
import com.zxplat.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 诊断「身体部位」主数据的应用编排：树形选项、平面列表、详情与生命周期（启停删）。
 *
 * <p>职责：Request/DTO 与领域 Command、查询模型之间的映射及调用顺序；部位层级与状态规则由领域服务保证。</p>
 *
 * <p>历史菜单对应：{@code bodystructure_management} / {@code bodystructure}。</p>
 *
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BodyAppService {

    /** 身体部位读侧：树、平面列表与详情。 */
    private final BodyStructureQueryService bodyStructureQueryService;
    /** 身体部位写侧：新增、修改、启停与删除。 */
    private final BodyStructureDomainService bodyStructureDomainService;
    /** 请求/DTO ↔ 领域命令与查询模型映射。 */
    private final BodyMapper bodyStructureMapper;

    /**
     * 查询身体部位树形下拉（含子节点）。
     *
     * @return 树根选项列表（ 承载子树）
     */
    public List<OptionDto> getBodyStructureTree() {
        // Gate：只读树查询，无写侧门禁
        // Pre：读侧加载身体部位树模型
        List<BodyStructureTreeQueryModel> roots = bodyStructureQueryService.getBodyStructureTree();
        // Asm：映射为前端 OptionDto 树
        return bodyStructureMapper.toOptionTree(roots);
    }

    /**
     * 平面列表查询全部身体部位（不分页）。
     *
     * @return 部位 DTO 列表（过滤空元素）
     */
    public List<BodyDto> getBodyList() {
        // Gate：只读全量列表，无分页窗口
        // Pre：读侧平面列表（不分页）
        // Asm：过滤空元素并映射 DTO
        return bodyStructureQueryService.getList(null, false, Pageable.unpaged())
            .getContent()
            .stream()
            .filter(Objects::nonNull)
            .map(bodyStructureMapper::toDto)
            .collect(Collectors.toList());
    }

    /**
     * 按主键查询身体部位详情。
     *
     * @param id 主键
     * @return 详情 DTO
     * @throws ValidationException {@code id} 为空
     * @throws EntityNotFoundException 部位不存在或已删除
     */
    public BodyDto getBody(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("查询身体部位失败：id 不能为空");
        }
        // Pre：按 id 加载详情查询模型
        BodyStructureInfoQueryModel model = bodyStructureQueryService.getBodyStructureInfo(id);
        if (model == null) {
            throw new EntityNotFoundException("身体部位不存在或已删除");
        }
        // Asm：映射详情 DTO
        return bodyStructureMapper.toDto(model);
    }

    /**
     * 新建身体部位。
     *
     * @param request 名称等入参
     * @return 新建后详情
     * @throws ValidationException 请求体、名称或持久化结果不合法
     * @throws EntityNotFoundException 回读详情失败（写入成功但查询不到）
     */
    public BodyDto createBody(CreateBodyRequest request) {
        // Gate：请求体与部位名称必填
        if (request == null) {
            throw new ValidationException("创建身体部位失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("创建身体部位失败：部位名称不能为空");
        }
        // Pre：请求映射为领域写命令
        var command = bodyStructureMapper.toCreateCommand(request);
        // Cmd：领域新增或编辑统一入口
        Long id = bodyStructureDomainService.addOrEditBodyStructure(command);
        if (id == null) {
            throw new ValidationException("创建身体部位失败：保存失败");
        }
        // Asm：按新主键回读详情
        return getBody(id);
    }

    /**
     * 更新身体部位名称等信息。
     *
     * @param request 含主键与名称
     * @return 更新后详情
     * @throws ValidationException 主键或名称不合法
     * @throws EntityNotFoundException 回读时部位不存在
     */
    public BodyDto updateBody(UpdateBodyRequest request) {
        // Gate：主键与名称必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新身体部位失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("更新身体部位失败：部位名称不能为空");
        }
        // Pre：请求映射为领域写命令
        var command = bodyStructureMapper.toUpdateCommand(request);
        // Cmd：领域更新（仍走 addOrEdit 统一入口）
        Long id = bodyStructureDomainService.addOrEditBodyStructure(command);
        // 业务分支：写侧未返回 id 时回退用请求 id 回读
        if (id == null) {
            id = request.getId();
        }
        // Asm：回读最新详情
        return getBody(id);
    }

    /**
     * 逻辑删除身体部位。
     *
     * @param id 主键
     * @throws ValidationException {@code id} 为空
     */
    public void deleteBody(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("删除身体部位失败：id 不能为空");
        }
        // Cmd：领域逻辑删除（批量命令形态下单条）
        DeleteBodyStructureCommand command = new DeleteBodyStructureCommand();
        DeleteBodyStructureCommand.DeleteBodyStructureItemCommand item = new DeleteBodyStructureCommand.DeleteBodyStructureItemCommand();
        item.setId(id);
        command.setItems(java.util.Collections.singletonList(item));
        bodyStructureDomainService.deleteBodyStructure(command);
    }

    /**
     * 启用部位（发布状态）。
     *
     * @param id 主键
     * @return 最新详情
     * @throws ValidationException {@code id} 为空
     * @throws EntityNotFoundException 启用后回读失败
     */
    public BodyDto activeBody(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("启用身体部位失败：id 不能为空");
        }
        // Cmd：领域启用（发布）
        ActiveBodyStructureCommand command = new ActiveBodyStructureCommand();
        ActiveBodyStructureCommand.ActiveBodyStructureItemCommand item = new ActiveBodyStructureCommand.ActiveBodyStructureItemCommand();
        item.setId(id);
        command.setItems(java.util.Collections.singletonList(item));
        bodyStructureDomainService.activeBodyStructure(command);
        // Asm：回读启用后详情
        return getBody(id);
    }

    /**
     * 停用部位。
     *
     * @param id 主键
     * @return 最新详情
     * @throws ValidationException {@code id} 为空
     * @throws EntityNotFoundException 停用后回读失败
     */
    public BodyDto inactiveBodyStructure(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("停用身体部位失败：id 不能为空");
        }
        // Cmd：领域停用
        InactiveBodyStructureCommand command = new InactiveBodyStructureCommand();
        InactiveBodyStructureCommand.InactiveBodyStructureItemCommand item = new InactiveBodyStructureCommand.InactiveBodyStructureItemCommand();
        item.setId(id);
        command.setItems(java.util.Collections.singletonList(item));
        bodyStructureDomainService.inactiveBodyStructure(command);
        // Asm：回读停用后详情
        return getBody(id);
    }
}
