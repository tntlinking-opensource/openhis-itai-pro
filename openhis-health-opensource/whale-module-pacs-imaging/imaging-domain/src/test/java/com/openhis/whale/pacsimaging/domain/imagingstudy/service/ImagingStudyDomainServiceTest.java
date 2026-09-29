/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.imagingstudy.service;

import com.openhis.whale.pacsimaging.domain.imagingstudy.command.EnsureImagingStudyCommand;
import com.openhis.whale.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import com.openhis.whale.pacsimaging.domain.imagingstudy.repository.ImagingStudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImagingStudyDomainService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ImagingStudyDomainServiceTest {

    @Mock
    private ImagingStudyRepository imagingStudyRepository;

    @InjectMocks
    private ImagingStudyDomainService domainService;

    @Test
    void ensureStudy_sameWorkItemId_isIdempotentAndDoesNotInsertSecondRow() {
        EnsureImagingStudyCommand firstCmd = this.buildCommand(1001L, "1.2.3.4.5", "1.2.3.4.5.6");
        when(imagingStudyRepository.findByWorkItemIdAndTenantId(1001L, 1L)).thenReturn(Optional.empty());
        when(imagingStudyRepository.findByStudyInstanceUidAndTenantId("1.2.3.4.5", 1L)).thenReturn(Optional.empty());
        when(imagingStudyRepository.save(any(ImagingStudy.class))).thenAnswer(invocation -> {
            ImagingStudy entity = invocation.getArgument(0);
            entity.setId(5001L);
            return entity;
        });

        ImagingStudy first = domainService.ensureStudy(firstCmd);
        assertEquals(5001L, first.getId());
        assertEquals(1001L, first.getWorkItemId());

        ImagingStudy existing = new ImagingStudy();
        existing.setId(5001L);
        existing.setWorkItemId(1001L);
        existing.setStudyInstanceUid("1.2.3.4.5");
        existing.setTenantId(1L);

        EnsureImagingStudyCommand secondCmd = this.buildCommand(1001L, "1.2.3.4.5", "1.2.3.4.5.7");
        when(imagingStudyRepository.findByWorkItemIdAndTenantId(1001L, 1L)).thenReturn(Optional.of(existing));
        when(imagingStudyRepository.findByStudyInstanceUidAndTenantId("1.2.3.4.5", 1L)).thenReturn(Optional.of(existing));
        when(imagingStudyRepository.save(any(ImagingStudy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImagingStudy second = domainService.ensureStudy(secondCmd);

        assertSame(existing, second);
        assertEquals("1.2.3.4.5.7", second.getSeriesInstanceUid());
        ArgumentCaptor<ImagingStudy> saveCaptor = ArgumentCaptor.forClass(ImagingStudy.class);
        verify(imagingStudyRepository, times(2)).save(saveCaptor.capture());
        assertEquals(5001L, saveCaptor.getAllValues().get(0).getId());
        assertEquals(5001L, saveCaptor.getAllValues().get(1).getId());
        verify(imagingStudyRepository, never()).save(eq(null));
    }

    private EnsureImagingStudyCommand buildCommand(Long workItemId, String studyUid, String seriesUid) {
        EnsureImagingStudyCommand cmd = new EnsureImagingStudyCommand();
        cmd.setWorkItemId(workItemId);
        cmd.setStudyInstanceUid(studyUid);
        cmd.setSeriesInstanceUid(seriesUid);
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);
        return cmd;
    }
}
