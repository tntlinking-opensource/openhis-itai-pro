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

package com.openhis.health.application.basic.body.mapper;

import com.openhis.health.application.basic.body.dto.BodyDto;
import com.openhis.health.application.basic.body.request.CreateBodyRequest;
import com.openhis.health.application.basic.body.request.UpdateBodyRequest;
import com.openhis.health.domain.diagnosis.bodystructure.command.AddOrEditBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.entity.BodyStructure;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureInfoQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureListItemQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link BodyMapper} 手写实现：部位请求、实体与查询模型同 {@link BodyDto}、
 * {@link com.openhis.whale.core.application.dto.OptionDto} 树之间的转换。
 *
 * <p>发布状态枚举展示文案由 {@link EnumUtil} 解析；不包含启停删等业务编排。</p>
 */
@Component
public class BodyMapperImpl implements BodyMapper {

    /**
     * 身体部位实体转换为应用层 DTO。
     *
     * @param entity 领域实体
     * @return 身体部位 DTO；为空时返回 {@code null}
     */
    @Override
    public BodyDto toDto(BodyStructure entity) {
        if (entity == null) {
            return null;
        }
        BodyDto dto = new BodyDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setParentId(entity.getParentId());
        fillStatus(dto, entity.getStatusEnum());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        return dto;
    }

    /**
     * 身体部位列表查询模型转换为应用层 DTO。
     *
     * @param model 列表行查询模型
     * @return 身体部位 DTO；为空时返回 {@code null}
     */
    @Override
    public BodyDto toDto(BodyStructureListItemQueryModel model) {
        if (model == null) {
            return null;
        }
        BodyDto dto = new BodyDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setParentId(model.getParentId());
        fillStatus(dto, model.getStatusEnum());
        return dto;
    }

    /**
     * 身体部位详情查询模型转换为应用层 DTO。
     *
     * @param model 详情查询模型
     * @return 身体部位 DTO；为空时返回 {@code null}
     */
    @Override
    public BodyDto toDto(BodyStructureInfoQueryModel model) {
        if (model == null) {
            return null;
        }
        BodyDto dto = new BodyDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setParentId(model.getParentId());
        fillStatus(dto, model.getStatusEnum());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        return dto;
    }

    /**
     * 身体部位树模型转换为前端下拉树结构。
     *
     * @param roots 根节点列表
     * @return OptionDto 树
     */
    @Override
    public List<OptionDto> toOptionTree(List<BodyStructureTreeQueryModel> roots) {
        return toOptionTreeInternal(roots);
    }

    /**
     * 创建请求转换为新增/编辑统一命令（新增语义）。
     *
     * @param request 创建请求
     * @return 统一写命令
     */
    @Override
    public AddOrEditBodyStructureCommand toCreateCommand(CreateBodyRequest request) {
        AddOrEditBodyStructureCommand command = new AddOrEditBodyStructureCommand();
        AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand item =
            new AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand();
        if (request != null) {
            item.setName(request.getName());
            item.setParentId(request.getParentId());
        }
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 更新请求转换为新增/编辑统一命令（更新语义）。
     *
     * @param request 更新请求
     * @return 统一写命令
     */
    @Override
    public AddOrEditBodyStructureCommand toUpdateCommand(UpdateBodyRequest request) {
        AddOrEditBodyStructureCommand command = new AddOrEditBodyStructureCommand();
        AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand item =
            new AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand();
        if (request != null) {
            item.setId(request.getId());
            item.setName(request.getName());
            item.setParentId(request.getParentId());
        }
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 递归构建身体部位树形下拉。
     *
     * @param nodes 树节点列表
     * @return 对应 OptionDto 树
     */
    private static List<OptionDto> toOptionTreeInternal(List<BodyStructureTreeQueryModel> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        long idx = 0;
        List<OptionDto> result = new ArrayList<>(nodes.size());
        for (BodyStructureTreeQueryModel n : nodes) {
            if (n == null) {
                continue;
            }
            idx++;
            List<OptionDto> children = toOptionTreeInternal(n.getChildren());
            String optionValue = null;
            if (n.getId() != null) {
                optionValue = String.valueOf(n.getId());
            }
            result.add(new OptionDto(
                idx,
                optionValue,
                n.getName(),
                n.getBusNo(),
                children
            ));
        }
        return result;
    }

    /**
     * 统一填充状态码及状态显示文案。
     *
     * @param dto 目标 DTO
     * @param statusCode 持久化状态码
     */
    private static void fillStatus(BodyDto dto, String statusCode) {
        if (dto == null) {
            return;
        }
        dto.setStatusEnum(statusCode);
        PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, statusCode);
        String statusLabel = null;
        if (status != null) {
            statusLabel = status.getDisplay();
        }
        dto.setStatusEnumText(statusLabel);
    }

}
