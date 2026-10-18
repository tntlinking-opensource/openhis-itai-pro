/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.service;

import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.workitem.command.MarkPacsWorkItemHasMediaCommand;
import com.zxplat.plugin.pacs.domain.workitem.queries.PacsWorkItemSearchQuery;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.MediaSource;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.pacsimaging.domain.share.constants.ImagingSystemOperatorConstants;
import com.zxplat.plugin.pacsimaging.application.capture.dto.KeyImageRowDto;
import com.zxplat.plugin.pacsimaging.application.capture.dto.ModalityWorklistRowDto;
import com.zxplat.plugin.pacsimaging.application.capture.dto.PendingCaptureRowDto;
import com.zxplat.plugin.pacsimaging.application.capture.mapper.PacsImageCaptureMapper;
import com.zxplat.plugin.pacsimaging.application.capture.port.MinioKeyImageStore;
import com.zxplat.plugin.pacsimaging.application.capture.port.OrthancStowClient;
import com.zxplat.plugin.pacsimaging.application.capture.request.DeleteKeyImageRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.EnsureStudyRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.GetCaptureDetailRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.ListKeyImagesRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.ListModalityWorklistRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.ListPendingCaptureRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.OnCStoreReceivedRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.SaveKeyImageRequest;
import com.zxplat.plugin.pacsimaging.application.capture.request.UploadDicomRequest;
import com.zxplat.plugin.pacsimaging.application.capture.response.DeleteKeyImageResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.EnsureStudyResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.GetCaptureDetailResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.ListKeyImagesResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.ListModalityWorklistResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.ListPendingCaptureResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.OnCStoreReceivedResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.SaveKeyImageResponse;
import com.zxplat.plugin.pacsimaging.application.capture.response.UploadDicomResponse;
import com.zxplat.plugin.pacsimaging.application.config.share.ImagingOperatorResolver;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.command.BindImagingStudyFromCStoreCommand;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.command.EnsureImagingStudyCommand;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.service.ImagingStudyDomainService;
import com.zxplat.plugin.pacsimaging.domain.media.command.DeleteKeyImageCommand;
import com.zxplat.plugin.pacsimaging.domain.media.command.SaveKeyImageCommand;
import com.zxplat.plugin.pacsimaging.domain.media.entity.Media;
import com.zxplat.plugin.pacsimaging.domain.media.queries.MediaQueryService;
import com.zxplat.plugin.pacsimaging.domain.media.queries.model.MediaQueryModel;
import com.zxplat.plugin.pacsimaging.domain.media.service.MediaDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 影像采集（AS-PACS-IM-001）。
 *
 * <p>接入：{@code POST /api/app/pacsImageCapture/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsImageCaptureAppService {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PacsWorkItemDomainService pacsWorkItemDomainService;
    private final ImagingStudyDomainService imagingStudyDomainService;
    private final ImagingStudyQueryService imagingStudyQueryService;
    private final MediaDomainService mediaDomainService;
    private final MediaQueryService mediaQueryService;
    private final OrthancStowClient orthancStowClient;
    private final MinioKeyImageStore minioKeyImageStore;
    private final PacsImageCaptureMapper pacsImageCaptureMapper;
    private final ImagingOperatorResolver imagingOperatorResolver;
    private final PatientQueryService patientQueryService;

    /**
     * IF-PACS-CAP-01：待采集列表（已检且尚无 Study 或无关键图）。
     *
     * @param request 分页过滤
     * @return 分页待采集行
     */
    public ListPendingCaptureResponse listPendingCapture(ListPendingCaptureRequest request) {
        // Gate：分页窗口须为正
        this.requirePositivePageWindow(request.getPageNo(), request.getPageSize());
        // Pre：查已检分页，再按 Study/关键图过滤待采集
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setModalityCode(request.getModalityCode());
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
        Page<PacsWorkItemQueryModel> completedPage = pacsWorkItemQueryService.searchCompleted(query, pageable);
        List<PendingCaptureRowDto> pendingRows = new ArrayList<>();
        for (PacsWorkItemQueryModel row : completedPage.getContent()) {
            // 逐条判定：无 Study 或无关键图 → 待采集
            boolean hasStudy = imagingStudyQueryService.findByWorkItemId(row.getId()).isPresent();
            long keyImageCount = mediaQueryService.countByWorkItemId(row.getId());
            if (!hasStudy || keyImageCount == 0L) {
                pendingRows.add(pacsImageCaptureMapper.toPendingRow(row, hasStudy, keyImageCount));
            }
        }
        // Asm：组装分页响应（本页内过滤；总数取本页过滤后条数，保持简单）
        ListPendingCaptureResponse response = new ListPendingCaptureResponse();
        response.setContent(pendingRows);
        response.setTotalCount(pendingRows.size());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        return response;
    }

    /**
     * IF-PACS-CAP-03：采集详情（工单 + Study 摘要 + 关键图条数）。
     *
     * @param request 工单主键
     * @return 详情
     */
    public GetCaptureDetailResponse getCaptureDetail(GetCaptureDetailRequest request) {
        // Gate：工单主键必填
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        // Pre：装载工单详情与 Study/关键图摘要
        PacsWorkItemDetailQueryModel detail = pacsWorkItemQueryService.getDetail(request.getWorkItemId());
        ImagingStudyQueryModel study = imagingStudyQueryService.findByWorkItemId(detail.getId()).orElse(null);
        long keyImageCount = mediaQueryService.countByWorkItemId(detail.getId());
        // Asm：装配详情
        return pacsImageCaptureMapper.toCaptureDetail(detail, study, keyImageCount);
    }

    /**
     * IF-PACS-CAP-06：绑定 Study/Series 索引并回写 hasMedia。
     *
     * @param request 绑定入参
     * @return 绑定结果
     */
    @Transactional
    public EnsureStudyResponse ensureStudy(EnsureStudyRequest request) {
        // Gate：参数与已检态
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(request.getStudyInstanceUid())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "studyInstanceUid 不能为空");
        }
        PacsWorkItemDetailQueryModel detail = this.requireCompletedWorkItem(request.getWorkItemId());
        Long operatorId = imagingOperatorResolver.requireOperatorId();
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Cmd：幂等确保 Study + 回写 hasMedia
        EnsureImagingStudyCommand ensureCmd = new EnsureImagingStudyCommand();
        ensureCmd.setWorkItemId(detail.getId());
        ensureCmd.setStudyInstanceUid(request.getStudyInstanceUid());
        ensureCmd.setSeriesInstanceUid(request.getSeriesInstanceUid());
        ensureCmd.setAccessionNumber(detail.getAccessionNumber());
        ensureCmd.setModalityCode(detail.getModalityCode());
        ensureCmd.setTenantId(tenantId);
        ensureCmd.setOperatorId(operatorId);
        ImagingStudy study = imagingStudyDomainService.ensureStudy(ensureCmd);
        this.markHasMedia(detail.getId(), Boolean.TRUE, operatorId);
        // Asm：装配响应
        return pacsImageCaptureMapper.toEnsureStudyResponse(study, Boolean.TRUE);
    }

    /**
     * IF-PACS-CAP-09：上传 DCM（STOW → ensureStudy → hasMedia）；真实 Orthanc 留给 57。
     *
     * @param request 上传入参
     * @return 上传结果
     */
    @Transactional
    public UploadDicomResponse uploadDicom(UploadDicomRequest request) {
        // Gate：参数与已检态
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(request.getContentBase64())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "DICOM 内容不能为空");
        }
        PacsWorkItemDetailQueryModel detail = this.requireCompletedWorkItem(request.getWorkItemId());
        Long operatorId = imagingOperatorResolver.requireOperatorId();
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：解码字节并 STOW（失败不写库）
        byte[] dicomBytes = this.decodeBase64(request.getContentBase64(), ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED);
        OrthancStowClient.StowResult stowResult = orthancStowClient.store(dicomBytes);
        if (stowResult == null || StringUtils.isEmpty(stowResult.studyInstanceUid())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "Orthanc STOW 未返回 Study UID");
        }
        // Cmd：绑定 Study + hasMedia
        EnsureImagingStudyCommand ensureCmd = new EnsureImagingStudyCommand();
        ensureCmd.setWorkItemId(detail.getId());
        ensureCmd.setStudyInstanceUid(stowResult.studyInstanceUid());
        ensureCmd.setSeriesInstanceUid(stowResult.seriesInstanceUid());
        ensureCmd.setAccessionNumber(detail.getAccessionNumber());
        ensureCmd.setModalityCode(detail.getModalityCode());
        ensureCmd.setTenantId(tenantId);
        ensureCmd.setOperatorId(operatorId);
        ImagingStudy study = imagingStudyDomainService.ensureStudy(ensureCmd);
        this.markHasMedia(detail.getId(), Boolean.TRUE, operatorId);
        // Asm：装配上传响应
        return pacsImageCaptureMapper.toUploadDicomResponse(study, Boolean.TRUE);
    }

    /**
     * IF-PACS-CAP-10：C-STORE 到达后按检查号绑定 Study 并回写 hasMedia。
     *
     * <p>无登录会话时操作人回落 {@link ImagingSystemOperatorConstants#SYSTEM_OPERATOR_ID}，
     * 表示 Orthanc/系统回调占位，禁止业务代码写裸 {@code 0L}。</p>
     *
     * @param request C-STORE 回写入参
     * @return 回写结果
     */
    @Transactional
    public OnCStoreReceivedResponse onCStoreReceived(OnCStoreReceivedRequest request) {
        // Gate：检查号与 Study UID 必填
        if (request == null || StringUtils.isEmpty(request.getAccessionNumber())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "accessionNumber 不能为空");
        }
        if (StringUtils.isEmpty(request.getStudyInstanceUid())) {
            throw new BusinessException(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, "studyInstanceUid 不能为空");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：按检查号定位工单；系统回调无登录时操作人回落系统占位 ID
        PacsWorkItemDetailQueryModel detail = pacsWorkItemQueryService
            .findByAccessionNumber(request.getAccessionNumber(), tenantId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "检查工单不存在"));
        Long operatorId = imagingOperatorResolver.resolveOperatorId()
            .orElse(ImagingSystemOperatorConstants.SYSTEM_OPERATOR_ID);
        // Cmd：bindFromCStore + hasMedia
        BindImagingStudyFromCStoreCommand bindCmd = new BindImagingStudyFromCStoreCommand();
        bindCmd.setWorkItemId(detail.getId());
        bindCmd.setStudyInstanceUid(request.getStudyInstanceUid());
        bindCmd.setSeriesInstanceUid(request.getSeriesInstanceUid());
        bindCmd.setAccessionNumber(detail.getAccessionNumber());
        if (StringUtils.isNotEmpty(request.getModalityCode())) {
            bindCmd.setModalityCode(request.getModalityCode());
        } else {
            bindCmd.setModalityCode(detail.getModalityCode());
        }
        bindCmd.setTenantId(tenantId);
        bindCmd.setOperatorId(operatorId);
        ImagingStudy study = imagingStudyDomainService.bindFromCStore(bindCmd);
        this.markHasMedia(detail.getId(), Boolean.TRUE, operatorId);
        // Asm：装配 C-STORE 回写响应
        return pacsImageCaptureMapper.toOnCStoreResponse(detail.getId(), study, Boolean.TRUE);
    }

    /**
     * IF-PACS-CAP-07（最小）：MWL 待扫工单查询，供设备/同步脚本拉取；不生成 DICOM .wl。
     *
     * @param request 分页与模态/科室过滤
     * @return 分页待扫行
     */
    public ListModalityWorklistResponse listModalityWorklist(ListModalityWorklistRequest request) {
        // Gate：分页窗口须为正
        this.requirePositivePageWindow(request.getPageNo(), request.getPageSize());
        // Pre：待执行（scheduled / registered+accession），模态由查询条件过滤
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setModalityCode(request.getModalityCode());
        PageRequest pageable = PageRequest.of(request.getPageNo() - 1, request.getPageSize());
        Page<PacsWorkItemQueryModel> page = pacsWorkItemQueryService.searchPendingExecution(query, pageable);
        // Pre：批量取姓名；失败不阻断
        Map<Long, String> names = this.resolvePatientNamesQuietly(page.getContent());
        List<ModalityWorklistRowDto> rows = new ArrayList<>();
        for (PacsWorkItemQueryModel row : page.getContent()) {
            String patientName = null;
            if (row.getPatientId() != null) {
                patientName = names.get(row.getPatientId());
            }
            rows.add(pacsImageCaptureMapper.toModalityWorklistRow(row, patientName));
        }
        // Asm：装配分页响应
        ListModalityWorklistResponse response = new ListModalityWorklistResponse();
        response.setContent(rows);
        response.setTotalCount(page.getTotalElements());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        return response;
    }

    /**
     * IF-PACS-CAP-13：保存关键图（MinIO put → Media 索引 → hasMedia）。
     *
     * @param request 保存入参
     * @return 保存结果
     */
    @Transactional
    public SaveKeyImageResponse saveKeyImage(SaveKeyImageRequest request) {
        // Gate：参数非空、类型为图片
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(request.getContentBase64())) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "contentBase64 不能为空");
        }
        if (StringUtils.isEmpty(request.getContentType())) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "contentType 不能为空");
        }
        // Pre：已检门禁 + 解码
        PacsWorkItemDetailQueryModel detail = this.requireCompletedWorkItem(request.getWorkItemId());
        Long operatorId = imagingOperatorResolver.requireOperatorId();
        Long tenantId = TenantUtils.getCurrentTenantId();
        byte[] bytes = this.decodeBase64(request.getContentBase64(), ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED);
        String sourceEnum = request.getSourceEnum();
        if (StringUtils.isEmpty(sourceEnum)) {
            sourceEnum = MediaSource.KEY_IMAGE.getCode();
        }
        // —：对象存储写入；失败则不写库
        String objectKey;
        try {
            objectKey = minioKeyImageStore.put(detail.getId(), request.getContentType().trim(), bytes);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "关键图对象存储写入失败");
        }
        if (StringUtils.isEmpty(objectKey)) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "关键图对象键为空");
        }
        // Cmd：Media 索引 + hasMedia
        SaveKeyImageCommand saveCmd = new SaveKeyImageCommand();
        saveCmd.setWorkItemId(detail.getId());
        saveCmd.setObjectKey(objectKey);
        saveCmd.setContentType(request.getContentType().trim());
        saveCmd.setSourceEnum(sourceEnum);
        saveCmd.setTenantId(tenantId);
        saveCmd.setOperatorId(operatorId);
        Media media = mediaDomainService.saveKeyImage(saveCmd);
        this.markHasMedia(detail.getId(), Boolean.TRUE, operatorId);
        // Asm：装配保存关键图响应
        return pacsImageCaptureMapper.toSaveKeyImageResponse(media, Boolean.TRUE);
    }

    /**
     * IF-PACS-CAP-14：删除关键图；删光且无 Study 时 hasMedia=false。
     *
     * @param request 删除入参
     * @return 删除后 hasMedia
     */
    @Transactional
    public DeleteKeyImageResponse deleteKeyImage(DeleteKeyImageRequest request) {
        // Gate：主键必填
        if (request == null || request.getWorkItemId() == null || request.getMediaId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_NOT_FOUND, "mediaId/workItemId 不能为空");
        }
        Long operatorId = imagingOperatorResolver.requireOperatorId();
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：定位关键图对象键
        MediaQueryModel target = this.findMediaOrThrow(request.getWorkItemId(), request.getMediaId());
        // —：先删对象存储
        minioKeyImageStore.delete(target.getObjectKey());
        // Cmd：删索引
        DeleteKeyImageCommand deleteCmd = new DeleteKeyImageCommand();
        deleteCmd.setMediaId(request.getMediaId());
        deleteCmd.setWorkItemId(request.getWorkItemId());
        deleteCmd.setTenantId(tenantId);
        deleteCmd.setOperatorId(operatorId);
        mediaDomainService.deleteKeyImage(deleteCmd);
        // Cmd：若无 Study 且关键图已空 → hasMedia=false
        boolean hasStudy = imagingStudyQueryService.findByWorkItemId(request.getWorkItemId()).isPresent();
        long remain = mediaQueryService.countByWorkItemId(request.getWorkItemId());
        Boolean hasMedia = Boolean.TRUE;
        if (!hasStudy && remain == 0L) {
            this.markHasMedia(request.getWorkItemId(), Boolean.FALSE, operatorId);
            hasMedia = Boolean.FALSE;
        }
        // Asm：装配删除后 hasMedia
        DeleteKeyImageResponse response = new DeleteKeyImageResponse();
        response.setHasMedia(hasMedia);
        return response;
    }

    /**
     * IF-PACS-CAP-15：关键图列表（含预签名 URL）。
     *
     * @param request 工单主键
     * @return 关键图行列表
     */
    public ListKeyImagesResponse listKeyImages(ListKeyImagesRequest request) {
        // Gate：工单主键必填
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        // Pre：列出索引并为每条签发 URL
        List<MediaQueryModel> models = mediaQueryService.listByWorkItemId(request.getWorkItemId());
        List<KeyImageRowDto> rows = new ArrayList<>();
        for (MediaQueryModel model : models) {
            String url = minioKeyImageStore.presignGet(model.getObjectKey());
            rows.add(pacsImageCaptureMapper.toKeyImageRow(model, url));
        }
        // Asm：装配关键图行列表
        ListKeyImagesResponse response = new ListKeyImagesResponse();
        response.setRecords(rows);
        return response;
    }

    /**
     * 批量解析患者姓名；任意异常返回空 Map，不阻断 MWL 列表。
     *
     * @param rows 工单行
     * @return patientId → name
     */
    private Map<Long, String> resolvePatientNamesQuietly(List<PacsWorkItemQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> patientIds = new ArrayList<>();
        for (PacsWorkItemQueryModel row : rows) {
            if (row != null && row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return patientQueryService.findPatientNamesByIds(patientIds);
        } catch (RuntimeException ex) {
            return Collections.emptyMap();
        }
    }

    /**
     * 断言工单已检；未检抛 {@code PACS_WORK_ITEM_INVALID_STATUS}。
     *
     * @param workItemId 工单主键
     * @return 已检工单详情
     */
    private PacsWorkItemDetailQueryModel requireCompletedWorkItem(Long workItemId) {
        PacsWorkItemDetailQueryModel detail = pacsWorkItemQueryService.getDetail(workItemId);
        if (!PacsWorkItemStatus.COMPLETED.getCode().equals(detail.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "未检工单不能采集");
        }
        return detail;
    }

    /**
     * 回写工单 hasMedia 标志。
     *
     * @param workItemId 工单主键
     * @param hasMedia 标志值
     * @param operatorId 操作人
     */
    private void markHasMedia(Long workItemId, Boolean hasMedia, Long operatorId) {
        MarkPacsWorkItemHasMediaCommand cmd = new MarkPacsWorkItemHasMediaCommand();
        cmd.setWorkItemId(workItemId);
        cmd.setHasMedia(hasMedia);
        cmd.setOperatorId(operatorId);
        pacsWorkItemDomainService.markHasMedia(cmd);
    }

    /**
     * 在工单下按 mediaId 定位关键图读模型。
     *
     * @param workItemId 工单主键
     * @param mediaId 关键图主键
     * @return 命中的读模型
     */
    private MediaQueryModel findMediaOrThrow(Long workItemId, Long mediaId) {
        List<MediaQueryModel> models = mediaQueryService.listByWorkItemId(workItemId);
        for (MediaQueryModel model : models) {
            if (mediaId.equals(model.getId())) {
                return model;
            }
        }
        throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_NOT_FOUND, "关键图索引不存在或不属于该工单");
    }

    /**
     * 校验分页参数为正整数。
     *
     * @param pageNo 页码
     * @param pageSize 每页条数
     */
    private void requirePositivePageWindow(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo <= 0 || pageSize == null || pageSize <= 0) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "分页参数非法");
        }
    }

    /**
     * Base64 解码；失败映射为指定业务错误码。
     *
     * @param contentBase64 Base64 文本
     * @param errorCode 失败错误码
     * @return 字节
     */
    private byte[] decodeBase64(String contentBase64, String errorCode) {
        try {
            return Base64.getDecoder().decode(contentBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(errorCode, "Base64 内容非法");
        }
    }
}
