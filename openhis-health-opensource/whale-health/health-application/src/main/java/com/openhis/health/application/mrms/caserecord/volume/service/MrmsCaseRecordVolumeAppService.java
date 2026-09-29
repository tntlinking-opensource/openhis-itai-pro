/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.volume.service;

import com.openhis.health.application.mrms.caserecord.volume.dto.VolumeCatalogDto;
import com.openhis.health.application.mrms.caserecord.volume.dto.VolumePdfResultDto;
import com.openhis.health.application.mrms.caserecord.volume.mapper.VolumeMapper;
import com.openhis.health.application.mrms.caserecord.volume.request.GetVolumeCatalogRequest;
import com.openhis.health.application.mrms.caserecord.volume.request.PreviewVolumeRequest;
import com.openhis.health.application.mrms.caserecord.volume.support.VolumePdfComposer;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentListExtensionIn;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 病案卷宗应用服务（IF-MR-Volume）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordVolume/*}。提供卷宗目录（首页虚拟项 + EMR 文书）
 * 与合并 PDF 预览；仅质控中/已归档病案可访问。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordVolumeAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final VolumeMapper volumeMapper;
    private final VolumePdfComposer volumePdfComposer;
    private final MrmsOperatorResolver operatorResolver;
    private final EmrDocumentExtension emrDocumentExtension;

    /**
     * 查询卷宗目录：首页虚拟项 + 就诊下可打印 EMR 文书列表。
     *
     * @param request 含病案主键
     * @return 卷宗目录 DTO
     */
    public VolumeCatalogDto getVolumeCatalog(GetVolumeCatalogRequest request) {
        // Gate：病案主键必填且状态允许查看卷宗
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        assertVolumeAccessible(record);
        // Pre：EMR 启用时加载就诊文书快照
        List<EmrDocumentQueryExtensionOut> documents = List.of();
        // EMR 未启用时目录仅含首页虚拟项
        if (emrDocumentExtension.isEnabled()) {
            documents = emrDocumentExtension.listByEncounter(
                new EmrDocumentListExtensionIn()
                    .setPatientId(record.getPatientId())
                    .setEncounterId(record.getEncounterId()));
        }
        // Asm：映射目录 DTO
        return volumeMapper.toCatalog(record, documents);
    }

    /**
     * 卷宗 PDF 预览：合并首页 PDF 与选定/全部 EMR 文书。
     *
     * @param request 预览选项（是否含首页、文书 id 列表）
     * @return 合并 PDF Base64 与页数
     */
    public VolumePdfResultDto previewVolumePdf(PreviewVolumeRequest request) {
        // Gate：病案主键必填、状态允许、操作人已登录
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        assertVolumeAccessible(record);
        operatorResolver.requireOperatorId();
        // Pre+Asm：卷宗编排器合并 PDF 段
        return volumePdfComposer.compose(request, operatorResolver.resolveOperatorIdOrNull());
    }

    /**
     * 卷宗访问门禁：仅质控中或已归档可查看，防止草稿期泄露完整卷宗。
     *
     * @param record 病案主档
     */
    private void assertVolumeAccessible(CaseRecord record) {
        CaseRecordStatus status = record.getStatusEnum();
        // 质控中/已归档允许查看卷宗
        if (status == CaseRecordStatus.ARCHIVED) {
            return;
        }
        // 草稿/提交中等状态禁止泄露完整卷宗
        throw new BusinessException(MrmsErrorCodes.VOLUME_ACCESS_DENIED, "仅质控中或已归档病案可查看卷宗");
    }

    /**
     * 校验病案主键非空。
     *
     * @param caseRecordId 病案主档主键
     * @return 非 null 主键
     */
    private Long requireCaseRecordId(Long caseRecordId) {
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        return caseRecordId;
    }
}
