/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.mapper;

import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemQueryModel;
import com.openhis.whale.pacsimaging.application.capture.dto.KeyImageRowDto;
import com.openhis.whale.pacsimaging.application.capture.dto.ModalityWorklistRowDto;
import com.openhis.whale.pacsimaging.application.capture.dto.PendingCaptureRowDto;
import com.openhis.whale.pacsimaging.application.capture.response.EnsureStudyResponse;
import com.openhis.whale.pacsimaging.application.capture.response.GetCaptureDetailResponse;
import com.openhis.whale.pacsimaging.application.capture.response.OnCStoreReceivedResponse;
import com.openhis.whale.pacsimaging.application.capture.response.SaveKeyImageResponse;
import com.openhis.whale.pacsimaging.application.capture.response.UploadDicomResponse;
import com.openhis.whale.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.openhis.whale.pacsimaging.domain.media.entity.Media;
import com.openhis.whale.pacsimaging.domain.media.queries.model.MediaQueryModel;
import org.springframework.stereotype.Component;

/**
 * 影像采集 DTO 手工装配（非 MapStruct）。
 */
@Component
public class PacsImageCaptureMapper {

    /**
     * 已检工单行 → 待采集行。
     *
     * @param model 工单列表读模型
     * @param hasStudy 是否已有 Study
     * @param keyImageCount 关键图数量
     * @return 待采集行
     */
    public PendingCaptureRowDto toPendingRow(PacsWorkItemQueryModel model, boolean hasStudy, long keyImageCount) {
        PendingCaptureRowDto dto = new PendingCaptureRowDto();
        if (model == null) {
            return dto;
        }
        dto.setWorkItemId(model.getId());
        dto.setAccessionNumber(model.getAccessionNumber());
        dto.setPatientId(model.getPatientId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setModalityCode(model.getModalityCode());
        dto.setBodySiteCode(model.getBodySiteCode());
        dto.setPerformedTime(model.getPerformedTime());
        dto.setHasStudy(hasStudy);
        dto.setKeyImageCount(keyImageCount);
        return dto;
    }

    /**
     * 待执行工单行 → MWL 行。
     *
     * @param model 工单列表读模型
     * @param patientName 患者姓名（可空）
     * @return MWL 行
     */
    public ModalityWorklistRowDto toModalityWorklistRow(PacsWorkItemQueryModel model, String patientName) {
        ModalityWorklistRowDto dto = new ModalityWorklistRowDto();
        if (model == null) {
            return dto;
        }
        dto.setWorkItemId(model.getId());
        dto.setAccessionNumber(model.getAccessionNumber());
        dto.setPatientId(model.getPatientId());
        dto.setPatientName(patientName);
        dto.setModalityCode(model.getModalityCode());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setScheduledTime(model.getScheduledTime());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setBodySiteCode(model.getBodySiteCode());
        return dto;
    }

    /**
     * 工单详情 + Study/关键图摘要 → 采集详情响应。
     *
     * @param detail 工单详情
     * @param study Study 读模型（可空）
     * @param keyImageCount 关键图数量
     * @return 详情响应
     */
    public GetCaptureDetailResponse toCaptureDetail(
        PacsWorkItemDetailQueryModel detail,
        ImagingStudyQueryModel study,
        long keyImageCount) {
        GetCaptureDetailResponse resp = new GetCaptureDetailResponse();
        if (detail == null) {
            return resp;
        }
        resp.setWorkItemId(detail.getId());
        resp.setAccessionNumber(detail.getAccessionNumber());
        resp.setPatientId(detail.getPatientId());
        resp.setOrganizationId(detail.getOrganizationId());
        resp.setStatusEnum(detail.getStatusEnum());
        resp.setModalityCode(detail.getModalityCode());
        resp.setBodySiteCode(detail.getBodySiteCode());
        resp.setHasMedia(detail.getHasMedia());
        resp.setKeyImageCount(keyImageCount);
        if (study != null) {
            resp.setStudyInstanceUid(study.getStudyInstanceUid());
            resp.setSeriesInstanceUid(study.getSeriesInstanceUid());
        }
        return resp;
    }

    /**
     * Study 聚合 → 绑定响应。
     *
     * @param study Study 聚合
     * @param hasMedia hasMedia 标志
     * @return 绑定响应
     */
    public EnsureStudyResponse toEnsureStudyResponse(ImagingStudy study, Boolean hasMedia) {
        EnsureStudyResponse resp = new EnsureStudyResponse();
        if (study == null) {
            return resp;
        }
        resp.setStudyId(study.getId());
        resp.setStudyInstanceUid(study.getStudyInstanceUid());
        resp.setSeriesInstanceUid(study.getSeriesInstanceUid());
        resp.setHasMedia(hasMedia);
        return resp;
    }

    /**
     * Study 聚合 → 上传 DCM 响应。
     *
     * @param study Study 聚合
     * @param hasMedia hasMedia 标志
     * @return 上传响应
     */
    public UploadDicomResponse toUploadDicomResponse(ImagingStudy study, Boolean hasMedia) {
        UploadDicomResponse resp = new UploadDicomResponse();
        if (study == null) {
            return resp;
        }
        resp.setStudyId(study.getId());
        resp.setStudyInstanceUid(study.getStudyInstanceUid());
        resp.setSeriesInstanceUid(study.getSeriesInstanceUid());
        resp.setHasMedia(hasMedia);
        return resp;
    }

    /**
     * C-STORE 回写 → 响应。
     *
     * @param workItemId 工单主键
     * @param study Study 聚合
     * @param hasMedia hasMedia 标志
     * @return 回写响应
     */
    public OnCStoreReceivedResponse toOnCStoreResponse(Long workItemId, ImagingStudy study, Boolean hasMedia) {
        OnCStoreReceivedResponse resp = new OnCStoreReceivedResponse();
        resp.setWorkItemId(workItemId);
        resp.setHasMedia(hasMedia);
        if (study != null) {
            resp.setStudyId(study.getId());
            resp.setStudyInstanceUid(study.getStudyInstanceUid());
        }
        return resp;
    }

    /**
     * Media 聚合 → 保存关键图响应。
     *
     * @param media Media 聚合
     * @param hasMedia hasMedia 标志
     * @return 保存响应
     */
    public SaveKeyImageResponse toSaveKeyImageResponse(Media media, Boolean hasMedia) {
        SaveKeyImageResponse resp = new SaveKeyImageResponse();
        if (media == null) {
            return resp;
        }
        resp.setMediaId(media.getId());
        resp.setObjectKey(media.getObjectKey());
        resp.setHasMedia(hasMedia);
        return resp;
    }

    /**
     * Media 读模型 + 预签名 URL → 关键图行。
     *
     * @param model Media 读模型
     * @param url 预签名 URL
     * @return 关键图行
     */
    public KeyImageRowDto toKeyImageRow(MediaQueryModel model, String url) {
        KeyImageRowDto dto = new KeyImageRowDto();
        if (model == null) {
            return dto;
        }
        dto.setMediaId(model.getId());
        dto.setObjectKey(model.getObjectKey());
        dto.setContentType(model.getContentType());
        dto.setSourceEnum(model.getSourceEnum());
        dto.setUrl(url);
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }
}
