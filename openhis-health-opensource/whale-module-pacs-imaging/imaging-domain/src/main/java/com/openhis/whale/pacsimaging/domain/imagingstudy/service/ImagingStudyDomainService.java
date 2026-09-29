/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.imagingstudy.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.pacsimaging.domain.imagingstudy.command.BindImagingStudyFromCStoreCommand;
import com.openhis.whale.pacsimaging.domain.imagingstudy.command.EnsureImagingStudyCommand;
import com.openhis.whale.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.openhis.whale.pacsimaging.domain.imagingstudy.repository.ImagingStudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ImagingStudy 写侧领域服务。
 *
 * <p>职责：幂等绑定 Study/Series 索引到工单；不写 Orthanc、不写像素、不发布事件。</p>
 */
@Service
@RequiredArgsConstructor
public class ImagingStudyDomainService {

    private final ImagingStudyRepository imagingStudyRepository;

    /**
     * 幂等确保工单对应一条 ImagingStudy。
     *
     * <p>注释锚点：按工单查 → 按 UID 查 → 插入或补 Series。</p>
     *
     * @param cmd 确保命令
     * @return 已有或新插入的聚合根
     */
    public ImagingStudy ensureStudy(EnsureImagingStudyCommand cmd) {
        this.validateEnsureCommand(cmd);
        // 按工单查
        Optional<ImagingStudy> byWorkItem = imagingStudyRepository
            .findByWorkItemIdAndTenantId(cmd.getWorkItemId(), cmd.getTenantId());
        // 按 UID 查
        Optional<ImagingStudy> byUid = imagingStudyRepository
            .findByStudyInstanceUidAndTenantId(cmd.getStudyInstanceUid().trim(), cmd.getTenantId());
        if (byWorkItem.isPresent() && byUid.isPresent()
            && !byWorkItem.get().getId().equals(byUid.get().getId())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "工单与 Study UID 指向不同索引行");
        }
        if (byWorkItem.isPresent()) {
            ImagingStudy existing = byWorkItem.get();
            existing.fillSeries(cmd.getSeriesInstanceUid());
            existing.setUpdatedBy(cmd.getOperatorId());
            return imagingStudyRepository.save(existing);
        }
        if (byUid.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "Study UID 已绑定其他工单");
        }
        // 插入
        ImagingStudy study = new ImagingStudy();
        study.bindIdentity(cmd.getWorkItemId(), cmd.getStudyInstanceUid());
        study.fillSeries(cmd.getSeriesInstanceUid());
        study.setAccessionNumber(cmd.getAccessionNumber());
        study.setModalityCode(cmd.getModalityCode());
        study.setTenantId(cmd.getTenantId());
        study.setCreatedBy(cmd.getOperatorId());
        study.setUpdatedBy(cmd.getOperatorId());
        return imagingStudyRepository.save(study);
    }

    /**
     * C-STORE 回写绑定（委托 ensureStudy，幂等）。
     *
     * @param cmd C-STORE 绑定命令
     * @return 已有或新插入的聚合根
     */
    public ImagingStudy bindFromCStore(BindImagingStudyFromCStoreCommand cmd) {
        EnsureImagingStudyCommand ensureCmd = new EnsureImagingStudyCommand();
        ensureCmd.setWorkItemId(cmd.getWorkItemId());
        ensureCmd.setStudyInstanceUid(cmd.getStudyInstanceUid());
        ensureCmd.setSeriesInstanceUid(cmd.getSeriesInstanceUid());
        ensureCmd.setAccessionNumber(cmd.getAccessionNumber());
        ensureCmd.setModalityCode(cmd.getModalityCode());
        ensureCmd.setTenantId(cmd.getTenantId());
        ensureCmd.setOperatorId(cmd.getOperatorId());
        return this.ensureStudy(ensureCmd);
    }

    private void validateEnsureCommand(EnsureImagingStudyCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "命令不能为空");
        }
        if (cmd.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(cmd.getStudyInstanceUid())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "studyInstanceUid 不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "tenantId 不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "operatorId 不能为空");
        }
    }
}
