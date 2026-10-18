/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.modality.service;

import com.zxplat.plugin.pacs.application.config.modality.dto.PacsCapabilityRowDto;
import com.zxplat.plugin.pacs.application.config.modality.mapper.PacsModalityConfigMapper;
import com.zxplat.plugin.pacs.application.config.modality.request.CapabilityIdRequest;
import com.zxplat.plugin.pacs.application.config.modality.request.SaveCapabilityRequest;
import com.zxplat.plugin.pacs.application.config.modality.request.SearchCapabilitiesRequest;
import com.zxplat.plugin.pacs.application.config.modality.response.SaveCapabilityResponse;
import com.zxplat.plugin.pacs.application.config.modality.response.SearchCapabilitiesResponse;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.plugin.pacs.domain.workitem.command.SaveModalityCapabilityCommand;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsModalityCapability;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsModalityCapabilityQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.ModalityCapabilityDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.ModalityCapabilityQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模态与可执行项目配置（AS-PACS-CFG-001）。
 *
 * <p>接入：{@code POST /api/app/pacsModalityConfig/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsModalityConfigAppService {

    private final ModalityCapabilityDomainService modalityCapabilityDomainService;
    private final ModalityCapabilityQueryService modalityCapabilityQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PacsModalityConfigMapper pacsModalityConfigMapper;

    /**
     * IF-PACS-CFG-01a：能力列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchCapabilitiesResponse searchCapabilities(SearchCapabilitiesRequest request) {
        // Gate：分页窗口须合法，避免全表扫描
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        // Pre：按模态/启停分页查能力（读侧 QS）
        Page<PacsModalityCapabilityQueryModel> page = modalityCapabilityQueryService.search(
            request.getModalityCode(), request.getEnabledOnly(), pageable);
        // Asm：映射出参
        List<PacsCapabilityRowDto> records = pacsModalityConfigMapper.toRowDtos(page.getContent());
        return PagedResultBuilder.build(new SearchCapabilitiesResponse(), records, page);
    }

    /**
     * IF-PACS-CFG-01b：保存能力。
     *
     * @param request 保存入参
     * @return 结果
     */
    @Transactional
    public SaveCapabilityResponse saveCapability(SaveCapabilityRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：新增或更新能力（含 activity 存在性校验）
        SaveModalityCapabilityCommand cmd = new SaveModalityCapabilityCommand();
        cmd.setId(request.getId());
        cmd.setActivityId(request.getActivityId());
        cmd.setModalityCode(request.getModalityCode());
        cmd.setEnabledFlag(request.getEnabledFlag());
        cmd.setOrganizationId(request.getOrganizationId());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setOperatorId(operatorId);
        PacsModalityCapability saved = modalityCapabilityDomainService.save(cmd);
        // Asm：保存结果
        return pacsModalityConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-01c：停用。
     *
     * @param request 主键
     * @return 结果
     */
    @Transactional
    public SaveCapabilityResponse disableCapability(CapabilityIdRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：停用能力（仍保留配置行）
        PacsModalityCapability saved = modalityCapabilityDomainService.disable(request.getId(), operatorId);
        // Asm：停用结果
        return pacsModalityConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-01d：软删。
     *
     * @param request 主键
     * @return 是否成功
     */
    @Transactional
    public Boolean deleteCapability(CapabilityIdRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：软删（有在途 orderLink 则拒绝）
        modalityCapabilityDomainService.softDelete(request.getId(), operatorId);
        // Asm：删除成功标志
        return Boolean.TRUE;
    }
}
