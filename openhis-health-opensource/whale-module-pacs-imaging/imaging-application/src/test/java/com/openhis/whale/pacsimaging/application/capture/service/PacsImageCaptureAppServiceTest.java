/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.service;

import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.workitem.command.MarkPacsWorkItemHasMediaCommand;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemDomainService;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsWorkItemStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.pacsimaging.application.capture.mapper.PacsImageCaptureMapper;
import com.openhis.whale.pacsimaging.application.capture.port.MinioKeyImageStore;
import com.openhis.whale.pacsimaging.application.capture.port.OrthancStowClient;
import com.openhis.whale.pacsimaging.application.capture.request.EnsureStudyRequest;
import com.openhis.whale.pacsimaging.application.capture.request.ListModalityWorklistRequest;
import com.openhis.whale.pacsimaging.application.capture.request.OnCStoreReceivedRequest;
import com.openhis.whale.pacsimaging.application.capture.request.SaveKeyImageRequest;
import com.openhis.whale.pacsimaging.application.capture.response.ListModalityWorklistResponse;
import com.openhis.whale.pacsimaging.application.capture.response.OnCStoreReceivedResponse;
import com.openhis.whale.pacsimaging.application.config.share.ImagingOperatorResolver;
import com.openhis.whale.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.openhis.whale.pacsimaging.domain.imagingstudy.service.ImagingStudyDomainService;
import com.openhis.whale.pacsimaging.domain.media.entity.Media;
import com.openhis.whale.pacsimaging.domain.media.queries.MediaQueryService;
import com.openhis.whale.pacsimaging.domain.media.service.MediaDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsImageCaptureAppService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsImageCaptureAppServiceTest {

    @Mock
    private PacsWorkItemQueryService pacsWorkItemQueryService;
    @Mock
    private PacsWorkItemDomainService pacsWorkItemDomainService;
    @Mock
    private ImagingStudyDomainService imagingStudyDomainService;
    @Mock
    private ImagingStudyQueryService imagingStudyQueryService;
    @Mock
    private MediaDomainService mediaDomainService;
    @Mock
    private MediaQueryService mediaQueryService;
    @Mock
    private OrthancStowClient orthancStowClient;
    @Mock
    private MinioKeyImageStore minioKeyImageStore;
    @Mock
    private ImagingOperatorResolver imagingOperatorResolver;
    @Mock
    private PatientQueryService patientQueryService;

    private PacsImageCaptureAppService appService;

    @BeforeEach
    void setUp() {
        this.appService = new PacsImageCaptureAppService(
            pacsWorkItemQueryService,
            pacsWorkItemDomainService,
            imagingStudyDomainService,
            imagingStudyQueryService,
            mediaDomainService,
            mediaQueryService,
            orthancStowClient,
            minioKeyImageStore,
            new PacsImageCaptureMapper(),
            imagingOperatorResolver,
            patientQueryService);
    }

    @Test
    void ensureStudy_rejectsNonCompletedWorkItem() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(101L);
        detail.setStatusEnum(PacsWorkItemStatus.IN_PROGRESS.getCode());
        when(pacsWorkItemQueryService.getDetail(101L)).thenReturn(detail);

        EnsureStudyRequest request = new EnsureStudyRequest();
        request.setWorkItemId(101L);
        request.setStudyInstanceUid("1.2.3");

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.ensureStudy(request));
        assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, ex.getErrorCode());
        verify(imagingStudyDomainService, never()).ensureStudy(any());
    }

    @Test
    void saveKeyImage_marksHasMediaTrueAfterMinioPut() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(201L);
        detail.setStatusEnum(PacsWorkItemStatus.COMPLETED.getCode());
        when(pacsWorkItemQueryService.getDetail(201L)).thenReturn(detail);
        when(imagingOperatorResolver.requireOperatorId()).thenReturn(9001L);
        when(minioKeyImageStore.put(any(), any(), any())).thenReturn("key-images/201/a.jpg");

        Media saved = new Media();
        saved.setId(88L);
        saved.setObjectKey("key-images/201/a.jpg");
        when(mediaDomainService.saveKeyImage(any())).thenReturn(saved);

        SaveKeyImageRequest request = new SaveKeyImageRequest();
        request.setWorkItemId(201L);
        request.setContentType("image/jpeg");
        request.setContentBase64(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            appService.saveKeyImage(request);
        }

        ArgumentCaptor<MarkPacsWorkItemHasMediaCommand> captor =
            ArgumentCaptor.forClass(MarkPacsWorkItemHasMediaCommand.class);
        verify(pacsWorkItemDomainService).markHasMedia(captor.capture());
        assertEquals(201L, captor.getValue().getWorkItemId());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getHasMedia()));
        assertEquals(9001L, captor.getValue().getOperatorId());
    }

    @Test
    void ensureStudy_idempotentPathMarksHasMedia() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(301L);
        detail.setAccessionNumber("ACC-301");
        detail.setModalityCode("CT");
        detail.setStatusEnum(PacsWorkItemStatus.COMPLETED.getCode());
        when(pacsWorkItemQueryService.getDetail(301L)).thenReturn(detail);
        when(imagingOperatorResolver.requireOperatorId()).thenReturn(9001L);

        ImagingStudy study = new ImagingStudy();
        study.setId(11L);
        study.setStudyInstanceUid("1.2.3.4");
        when(imagingStudyDomainService.ensureStudy(any())).thenReturn(study);

        EnsureStudyRequest request = new EnsureStudyRequest();
        request.setWorkItemId(301L);
        request.setStudyInstanceUid("1.2.3.4");

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            appService.ensureStudy(request);
        }

        verify(imagingStudyDomainService).ensureStudy(any());
        ArgumentCaptor<MarkPacsWorkItemHasMediaCommand> captor =
            ArgumentCaptor.forClass(MarkPacsWorkItemHasMediaCommand.class);
        verify(pacsWorkItemDomainService).markHasMedia(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getHasMedia()));
    }

    @Test
    void onCStoreReceived_bindsStudyAndMarksHasMedia() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(401L);
        detail.setAccessionNumber("ACC-401");
        detail.setModalityCode("CT");
        when(pacsWorkItemQueryService.findByAccessionNumber("ACC-401", 1L)).thenReturn(Optional.of(detail));
        when(imagingOperatorResolver.resolveOperatorId()).thenReturn(Optional.of(1L));

        ImagingStudy study = new ImagingStudy();
        study.setId(21L);
        study.setStudyInstanceUid("1.2.840.401");
        when(imagingStudyDomainService.bindFromCStore(any())).thenReturn(study);

        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-401");
        request.setStudyInstanceUid("1.2.840.401");

        OnCStoreReceivedResponse response;
        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            response = appService.onCStoreReceived(request);
        }

        assertEquals(401L, response.getWorkItemId());
        assertEquals("1.2.840.401", response.getStudyInstanceUid());
        assertTrue(Boolean.TRUE.equals(response.getHasMedia()));
        verify(imagingStudyDomainService).bindFromCStore(any());
        verify(pacsWorkItemDomainService).markHasMedia(any());
    }

    @Test
    void onCStoreReceived_idempotentSecondCallStillMarksHasMedia() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(402L);
        detail.setAccessionNumber("ACC-402");
        detail.setModalityCode("MR");
        when(pacsWorkItemQueryService.findByAccessionNumber("ACC-402", 1L)).thenReturn(Optional.of(detail));
        when(imagingOperatorResolver.resolveOperatorId()).thenReturn(Optional.empty());

        ImagingStudy study = new ImagingStudy();
        study.setId(22L);
        study.setStudyInstanceUid("1.2.840.402");
        when(imagingStudyDomainService.bindFromCStore(any())).thenReturn(study);

        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-402");
        request.setStudyInstanceUid("1.2.840.402");

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            appService.onCStoreReceived(request);
            appService.onCStoreReceived(request);
        }

        verify(imagingStudyDomainService, times(2)).bindFromCStore(any());
        verify(pacsWorkItemDomainService, times(2)).markHasMedia(any());
    }

    @Test
    void onCStoreReceived_throwsWhenWorkItemMissing() {
        when(pacsWorkItemQueryService.findByAccessionNumber("NO-ACC", 1L)).thenReturn(Optional.empty());

        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("NO-ACC");
        request.setStudyInstanceUid("1.2.3");

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> appService.onCStoreReceived(request));
            assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, ex.getErrorCode());
        }
        verify(imagingStudyDomainService, never()).bindFromCStore(any());
    }

    @Test
    void listModalityWorklist_returnsPendingRows() {
        PacsWorkItemQueryModel row = new PacsWorkItemQueryModel();
        row.setId(501L);
        row.setAccessionNumber("ACC-501");
        row.setPatientId(77L);
        row.setModalityCode("US");
        row.setOrganizationId(10L);
        row.setStatusEnum(PacsWorkItemStatus.SCHEDULED.getCode());
        row.setBodySiteCode("CHEST");
        when(pacsWorkItemQueryService.searchPendingExecution(any(), any()))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
        when(patientQueryService.findPatientNamesByIds(anyCollection())).thenReturn(Map.of(77L, "张三"));

        ListModalityWorklistRequest request = new ListModalityWorklistRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setModalityCode("US");

        ListModalityWorklistResponse response = appService.listModalityWorklist(request);

        assertEquals(1, response.getContent().size());
        assertEquals(501L, response.getContent().get(0).getWorkItemId());
        assertEquals("ACC-501", response.getContent().get(0).getAccessionNumber());
        assertEquals("张三", response.getContent().get(0).getPatientName());
        assertEquals("US", response.getContent().get(0).getModalityCode());
    }

    @Test
    void listModalityWorklist_continuesWhenPatientNameFails() {
        PacsWorkItemQueryModel row = new PacsWorkItemQueryModel();
        row.setId(502L);
        row.setAccessionNumber("ACC-502");
        row.setPatientId(88L);
        row.setModalityCode("CT");
        row.setStatusEnum(PacsWorkItemStatus.REGISTERED.getCode());
        when(pacsWorkItemQueryService.searchPendingExecution(any(), any()))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));
        when(patientQueryService.findPatientNamesByIds(anyCollection()))
            .thenThrow(new RuntimeException("db down"));

        ListModalityWorklistRequest request = new ListModalityWorklistRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        ListModalityWorklistResponse response = appService.listModalityWorklist(request);

        assertEquals(1, response.getContent().size());
        assertEquals(502L, response.getContent().get(0).getWorkItemId());
        assertEquals(null, response.getContent().get(0).getPatientName());
    }

    @Test
    void listModalityWorklist_rejectsInvalidPage() {
        ListModalityWorklistRequest request = new ListModalityWorklistRequest();
        request.setPageNo(0);
        request.setPageSize(10);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.listModalityWorklist(request));
        assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, ex.getErrorCode());
        verify(pacsWorkItemQueryService, never()).searchPendingExecution(any(), any());
    }
}
