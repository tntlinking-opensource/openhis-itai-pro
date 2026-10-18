/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.mapper;

import com.zxplat.application.basic.code.dto.CodeSystemDto;
import com.zxplat.application.basic.code.dto.ConceptDto;
import com.zxplat.application.basic.code.request.CreateCodeSystemRequest;
import com.zxplat.application.basic.code.request.CreateConceptRequest;
import com.zxplat.application.basic.code.request.UpdateCodeSystemRequest;
import com.zxplat.application.basic.code.request.UpdateConceptRequest;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.terminology.command.codesystem.CreateCodeSystemCommand;
import com.zxplat.domain.terminology.command.codesystem.UpdateCodeSystemCommand;
import com.zxplat.domain.terminology.command.concept.CreateConceptCommand;
import com.zxplat.domain.terminology.command.concept.UpdateConceptCommand;
import com.zxplat.domain.terminology.entity.CodeSystem;
import com.zxplat.domain.terminology.entity.Concept;
import org.springframework.stereotype.Component;

/**
 * 术语编码体系（编码系统与概念）的应用层映射：请求/响应 DTO ↔ 领域命令与实体。
 *
 * <p>不包含租户赋值逻辑（创建命令的 tenantId 由
 * {@link com.zxplat.application.basic.code.service.CodeAppService} 装配）；状态展示文案由本类私有方法解析。</p>
 *
 */
@Component
public class CodesystemMapper {

    /**
     * 创建编码体系请求 → 领域命令。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 创建命令或 {@code null}
     */
    public CreateCodeSystemCommand toCreateCommand(CreateCodeSystemRequest request) {
        // 映射器契约：空请求不产生匿名命令，由上层决定是否抛 ValidationException。
        if (request == null) {
            return null;
        }
        CreateCodeSystemCommand cmd = new CreateCodeSystemCommand();
        cmd.setName(request.getName());
        cmd.setTitle(request.getTitle());
        cmd.setDescription(request.getDescription());
        cmd.setVersion(request.getVersion());
        return cmd;
    }

    /**
     * 更新编码体系请求 → 领域命令。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 更新命令或 {@code null}
     */
    public UpdateCodeSystemCommand toUpdateCommand(UpdateCodeSystemRequest request) {
        // 空请求不生成更新命令，避免领域侧收到缺主键的半成品。
        if (request == null) {
            return null;
        }
        UpdateCodeSystemCommand cmd = new UpdateCodeSystemCommand();
        cmd.setId(request.getId());
        cmd.setTitle(request.getTitle());
        cmd.setDescription(request.getDescription());
        cmd.setVersion(request.getVersion());
        return cmd;
    }

    /**
     * 新增概念请求 → 领域命令。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 命令或 {@code null}
     */
    public CreateConceptCommand toCreateConceptCommand(CreateConceptRequest request) {
        // 空请求不产生概念创建命令。
        if (request == null) {
            return null;
        }
        CreateConceptCommand cmd = new CreateConceptCommand();
        cmd.setCodeSystemId(request.getCodeSystemId());
        cmd.setCode(request.getCode());
        cmd.setDisplay(request.getDisplay());
        cmd.setDefinition(request.getDefinition());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setIsActive(request.getIsActive());
        return cmd;
    }

    /**
     * 更新概念请求 → 领域命令。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 命令或 {@code null}
     */
    public UpdateConceptCommand toUpdateConceptCommand(UpdateConceptRequest request) {
        // 空请求不产生概念更新命令。
        if (request == null) {
            return null;
        }
        UpdateConceptCommand cmd = new UpdateConceptCommand();
        cmd.setId(request.getId());
        cmd.setCode(request.getCode());
        cmd.setDisplay(request.getDisplay());
        cmd.setDefinition(request.getDefinition());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setIsActive(request.getIsActive());
        return cmd;
    }

    /**
     * 编码体系实体 → API DTO（含状态码整型化与展示文案）。
     *
     * @param codeSystem 实体；{@code null} 返回 {@code null}
     * @return DTO 或 {@code null}
     */
    public CodeSystemDto toDto(CodeSystem codeSystem) {
        // 实体空则 DTO 空，调用方自行决定是否视为 404。
        if (codeSystem == null) {
            return null;
        }
        CodeSystemDto dto = new CodeSystemDto();
        dto.setId(codeSystem.getId());
        dto.setName(codeSystem.getName());
        dto.setTitle(codeSystem.getTitle());
        dto.setDescription(codeSystem.getDescription());
        dto.setVersion(codeSystem.getVersion());
        dto.setTenantId(codeSystem.getTenantId());
        dto.setCreatedAt(codeSystem.getCreatedAt());
        dto.setUpdatedAt(codeSystem.getUpdatedAt());

        Integer status = null;
        if (codeSystem.getStatus() != null) {
            // 持久化状态可能为数字字符串：转换失败则保留 null，避免前端收到非法整型。
            try {
                status = Integer.valueOf(codeSystem.getStatus());
            } catch (NumberFormatException ignored) {
                status = null;
            }
        }
        dto.setStatus(status);
        dto.setStatus_dictText(getStatusText(codeSystem.getStatus()));
        return dto;
    }

    /**
     * 将编码体系状态码映射为中文展示文案（基于 {@link PublicationStatus}）。
     *
     * @param statusCode 持久化状态字符串
     * @return 中文文案；未知码返回空串
     */
    private static String getStatusText(String statusCode) {
        if (PublicationStatus.ACTIVE.getCode().equals(statusCode)) {
            return PublicationStatus.ACTIVE.getDisplay();
        }
        if (PublicationStatus.DRAFT.getCode().equals(statusCode)) {
            return PublicationStatus.DRAFT.getDisplay();
        }
        if (PublicationStatus.INACTIVE.getCode().equals(statusCode)) {
            return PublicationStatus.INACTIVE.getDisplay();
        }
        if (PublicationStatus.DISCARDED.getCode().equals(statusCode)) {
            return PublicationStatus.DISCARDED.getDisplay();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /**
     * 概念实体 → API DTO（树与子概念列表在此路径置空，由查询侧另行组装）。
     *
     * @param concept 实体；{@code null} 返回 {@code null}
     * @return DTO 或 {@code null}
     */
    public ConceptDto toDto(Concept concept) {
        // 单条概念映射：树与子节点不在此路径填充。
        if (concept == null) {
            return null;
        }
        ConceptDto dto = new ConceptDto();
        dto.setId(concept.getId());
        dto.setCodeSystemId(concept.getCodeSystemId());
        dto.setCode(concept.getCode());
        dto.setDisplay(concept.getDisplay());
        dto.setDefinition(concept.getDefinition());
        dto.setSortOrder(concept.getSortOrder());
        dto.setIsActive(concept.getIsActive());
        dto.setCssClass(concept.getCssClass());
        dto.setListClass(concept.getListClass());
        dto.setIsDefault(concept.getIsDefault());
        dto.setCreatedAt(concept.getCreatedAt());
        dto.setUpdatedAt(concept.getUpdatedAt());
        Long parentId = null;
        if (concept.getParent() != null) {
            parentId = concept.getParent().getId();
        }
        dto.setParentId(parentId);
        dto.setChildren(null);
        dto.setCodeSystemName(null);
        return dto;
    }
}
