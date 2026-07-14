/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.stopclinic.service;

import com.openhis.whale.booking.domain.stopclinic.entity.StopClinicSlotFreeze;
import com.openhis.whale.booking.domain.stopclinic.repository.StopClinicSlotFreezeRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TC-FRZ-01：停诊预览冻结源 Slot 验收。
 */
@ExtendWith(MockitoExtension.class)
class StopClinicSlotFreezeDomainServiceTest {

    private static final Long ORG_ID = 100L;
    private static final Long SCHEDULE_ID = 200L;
    private static final Long SLOT_ID = 300L;
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private StopClinicSlotFreezeRepository stopClinicSlotFreezeRepository;

    @InjectMocks
    private StopClinicSlotFreezeDomainService domainService;

    @Test
    void applyPreviewFreeze_createsFreezeRecord() {
        when(stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(SLOT_ID))
            .thenReturn(Optional.empty());
        when(stopClinicSlotFreezeRepository.save(any(StopClinicSlotFreeze.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime expiresAt = domainService.applyPreviewFreeze(
            ORG_ID, SCHEDULE_ID, List.of(SLOT_ID), 15, OPERATOR_ID);

        assertThat(expiresAt).isAfter(LocalDateTime.now().plusMinutes(14));

        ArgumentCaptor<StopClinicSlotFreeze> captor = ArgumentCaptor.forClass(StopClinicSlotFreeze.class);
        verify(stopClinicSlotFreezeRepository).save(captor.capture());
        StopClinicSlotFreeze saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(saved.getSlotId()).isEqualTo(SLOT_ID);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.getIsDeleted()).isFalse();
    }

    @Test
    void assertSlotNotFrozenForNewBooking_rejectsWhenFrozen() {
        StopClinicSlotFreeze freeze = activeFreeze(LocalDateTime.now().plusMinutes(10));
        when(stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(SLOT_ID))
            .thenReturn(Optional.of(freeze));

        assertThatThrownBy(() -> domainService.assertSlotNotFrozenForNewBooking(SLOT_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("号源停诊预览冻结中，暂不可预约");

        verify(stopClinicSlotFreezeRepository, never()).save(any());
    }

    @Test
    void releasePreviewFreeze_allowsNewBookingAgain() {
        StopClinicSlotFreeze freeze = activeFreeze(LocalDateTime.now().plusMinutes(10));
        when(stopClinicSlotFreezeRepository.findByScheduleIdAndSlotIdInAndIsDeletedFalse(
            SCHEDULE_ID, List.of(SLOT_ID))).thenReturn(List.of(freeze));
        when(stopClinicSlotFreezeRepository.save(any(StopClinicSlotFreeze.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(SLOT_ID))
            .thenReturn(Optional.empty());

        domainService.releasePreviewFreeze(SCHEDULE_ID, List.of(SLOT_ID), OPERATOR_ID);
        domainService.assertSlotNotFrozenForNewBooking(SLOT_ID);

        assertThat(freeze.getIsDeleted()).isTrue();
        verify(stopClinicSlotFreezeRepository).save(freeze);
    }

    @Test
    void assertSlotNotFrozenForNewBooking_lazyCleansExpiredFreeze() {
        StopClinicSlotFreeze expired = activeFreeze(LocalDateTime.now().minusMinutes(1));
        when(stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(SLOT_ID))
            .thenReturn(Optional.of(expired));
        when(stopClinicSlotFreezeRepository.save(any(StopClinicSlotFreeze.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        domainService.assertSlotNotFrozenForNewBooking(SLOT_ID);

        assertThat(expired.getIsDeleted()).isTrue();
        verify(stopClinicSlotFreezeRepository).save(expired);
    }

    @Test
    void findActiveFrozenSlotIds_delegatesToRepository() {
        StopClinicSlotFreeze first = activeFreeze(LocalDateTime.now().plusMinutes(10));
        StopClinicSlotFreeze second = activeFreeze(LocalDateTime.now().plusMinutes(10));
        second.setSlotId(301L);
        when(stopClinicSlotFreezeRepository.findByScheduleIdAndIsDeletedFalseAndExpiresAtAfter(
            eq(SCHEDULE_ID), any())).thenReturn(List.of(first, second));

        Set<Long> frozen = domainService.findActiveFrozenSlotIdsByScheduleId(SCHEDULE_ID);

        assertThat(frozen).containsExactlyInAnyOrder(SLOT_ID, 301L);
    }

    private static StopClinicSlotFreeze activeFreeze(LocalDateTime expiresAt) {
        StopClinicSlotFreeze freeze = new StopClinicSlotFreeze();
        freeze.setOrganizationId(ORG_ID);
        freeze.setScheduleId(SCHEDULE_ID);
        freeze.setSlotId(SLOT_ID);
        freeze.setExpiresAt(expiresAt);
        freeze.setIsDeleted(Boolean.FALSE);
        return freeze;
    }
}
