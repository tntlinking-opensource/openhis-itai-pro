/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries;

import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.repository.ImagingStudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ImagingStudy 读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class ImagingStudyQueryService {

    private final ImagingStudyRepository imagingStudyRepository;

    /**
     * 按工单查询 Study 索引（0..1）。
     *
     * <p>注释锚点：软删过滤 → 一次仓储读取 → 组装 QueryModel。</p>
     *
     * @param workItemId 工单主键
     * @return 可选读模型
     */
    public Optional<ImagingStudyQueryModel> findByWorkItemId(Long workItemId) {
        if (workItemId == null) {
            return Optional.empty();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return imagingStudyRepository.findByWorkItemIdAndTenantId(workItemId, tenantId)
            .map(this::toModel);
    }

    private ImagingStudyQueryModel toModel(ImagingStudy study) {
        ImagingStudyQueryModel model = new ImagingStudyQueryModel();
        model.setId(study.getId());
        model.setWorkItemId(study.getWorkItemId());
        model.setStudyInstanceUid(study.getStudyInstanceUid());
        model.setSeriesInstanceUid(study.getSeriesInstanceUid());
        model.setAccessionNumber(study.getAccessionNumber());
        model.setModalityCode(study.getModalityCode());
        return model;
    }
}
