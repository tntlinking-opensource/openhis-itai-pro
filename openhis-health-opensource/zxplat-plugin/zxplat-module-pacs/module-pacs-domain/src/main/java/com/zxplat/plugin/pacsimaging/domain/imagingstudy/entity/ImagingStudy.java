/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.imagingstudy.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * DICOM Study 索引聚合根（{@code pacs_imaging_study}）。
 *
 * <p>不承载像素或患者姓名；仅维护 Study/Series UID 与工单绑定。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_imaging_study")
public class ImagingStudy extends BaseAuditedEntity {

    /** 检查工单主键；租户内唯一。 */
    @Column(name = "work_item_id", nullable = false)
    private Long workItemId;

    /** DICOM Study Instance UID；租户内唯一。 */
    @Column(name = "study_instance_uid", nullable = false)
    private String studyInstanceUid;

    /** 主 Series Instance UID；可后补。 */
    @Column(name = "series_instance_uid")
    private String seriesInstanceUid;

    /** 冗余检查号。 */
    @Column(name = "accession_number")
    private String accessionNumber;

    /** 模态码。 */
    @Column(name = "modality_code")
    private String modalityCode;

    /**
     * 绑定工单与 Study UID（BR-IMG-01/02）。
     *
     * @param workItemId 工单主键
     * @param studyInstanceUid Study Instance UID
     */
    public void bindIdentity(Long workItemId, String studyInstanceUid) {
        if (workItemId == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(studyInstanceUid)) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "studyInstanceUid 不能为空");
        }
        this.workItemId = workItemId;
        this.studyInstanceUid = studyInstanceUid.trim();
    }

    /**
     * 后补 Series UID；入参为空时不覆盖已有值。
     *
     * @param seriesInstanceUid Series Instance UID
     */
    public void fillSeries(String seriesInstanceUid) {
        if (StringUtils.isEmpty(seriesInstanceUid)) {
            return;
        }
        this.seriesInstanceUid = seriesInstanceUid.trim();
    }
}
