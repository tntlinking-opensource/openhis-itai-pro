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

package com.openhis.health.application.basic.body.service;

import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.health.application.basic.body.dto.BodyDto;
import com.openhis.health.application.basic.body.mapper.BodyMapper;
import com.openhis.health.application.basic.body.request.CreateBodyRequest;
import com.openhis.health.application.basic.body.request.UpdateBodyRequest;
import com.openhis.health.domain.diagnosis.bodystructure.command.ActiveBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.command.DeleteBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.command.InactiveBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureInfoQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureDomainService;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
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
        // 读侧树模型 → 前端统一 OptionDto 树，不做写命令。
        List<BodyStructureTreeQueryModel> roots = bodyStructureQueryService.getBodyStructureTree();
        return bodyStructureMapper.toOptionTree(roots);
    }

    /**
     * 平面列表查询全部身体部位（不分页）。
     *
     * @return 部位 DTO 列表（过滤空元素）
     */
    public List<BodyDto> getBodyList() {
        // 全量平面列表：不分页，仅供管理端简易表格；数据量大时应改为分页接口。
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
        if (id == null) {
            throw new ValidationException("查询身体部位失败：id 不能为空");
        }
        // 单条详情：查询模型 → DTO，缺失时明确 404 语义。
        BodyStructureInfoQueryModel model = bodyStructureQueryService.getBodyStructureInfo(id);
        if (model == null) {
            throw new EntityNotFoundException("身体部位不存在或已删除");
        }
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
        if (request == null) {
            throw new ValidationException("创建身体部位失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("创建身体部位失败：部位名称不能为空");
        }
        // 新增：Mapper → 领域统一写入口 addOrEdit → 按返回 id 回读详情。
        var command = bodyStructureMapper.toCreateCommand(request);
        Long id = bodyStructureDomainService.addOrEditBodyStructure(command);
        if (id == null) {
            throw new ValidationException("创建身体部位失败：保存失败");
        }
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
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新身体部位失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("更新身体部位失败：部位名称不能为空");
        }
        // 更新：仍走 addOrEdit；若写侧未返回 id，则回退用请求 id 回读（兼容领域实现差异）。
        var command = bodyStructureMapper.toUpdateCommand(request);
        Long id = bodyStructureDomainService.addOrEditBodyStructure(command);
        if (id == null) {
            // 写侧返回首条保存记录的 id；更新语义下如果返回空，回退使用请求 id 回读。
            id = request.getId();
        }
        return getBody(id);
    }

    /**
     * 逻辑删除身体部位。
     *
     * @param id 主键
     * @throws ValidationException {@code id} 为空
     */
    public void deleteBody(Long id) {
        if (id == null) {
            throw new ValidationException("删除身体部位失败：id 不能为空");
        }
        // 批量命令形态下单条删除：领域层统一逻辑删除语义。
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
        if (id == null) {
            throw new ValidationException("启用身体部位失败：id 不能为空");
        }
        // 状态迁移：发布（启用）→ 回读对外展示。
        ActiveBodyStructureCommand command = new ActiveBodyStructureCommand();
        ActiveBodyStructureCommand.ActiveBodyStructureItemCommand item = new ActiveBodyStructureCommand.ActiveBodyStructureItemCommand();
        item.setId(id);
        command.setItems(java.util.Collections.singletonList(item));
        bodyStructureDomainService.activeBodyStructure(command);
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
        if (id == null) {
            throw new ValidationException("停用身体部位失败：id 不能为空");
        }
        // 状态迁移：停用 → 回读对外展示。
        InactiveBodyStructureCommand command = new InactiveBodyStructureCommand();
        InactiveBodyStructureCommand.InactiveBodyStructureItemCommand item = new InactiveBodyStructureCommand.InactiveBodyStructureItemCommand();
        item.setId(id);
        command.setItems(java.util.Collections.singletonList(item));
        bodyStructureDomainService.inactiveBodyStructure(command);
        return getBody(id);
    }
}
