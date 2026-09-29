/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.service;

import com.openhis.health.domain.pacs.workitem.command.CompletePacsExamCommand;
import com.openhis.health.domain.pacs.workitem.command.CreatePacsOrderLinkItem;
import com.openhis.health.domain.pacs.workitem.command.CreatePacsWorkItemCommand;
import com.openhis.health.domain.pacs.workitem.command.CreateLocalPacsWorkItemCommand;
import com.openhis.health.domain.pacs.workitem.entity.PacsWorkItem;
import com.openhis.health.domain.pacs.workitem.entity.PacsWorkItemOrder;
import com.openhis.health.domain.pacs.workitem.repository.PacsWorkItemOrderRepository;
import com.openhis.health.domain.pacs.workitem.repository.PacsWorkItemRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsWorkItemStatus;
import com.openhis.health.domain.share.events.pacs.workitem.PacsWorkItemCreatedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsWorkItemDomainService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsWorkItemDomainServiceTest {

    @Mock
    private PacsWorkItemRepository workItemRepository;

    @Mock
    private PacsWorkItemOrderRepository orderRepository;

    @Mock
    private ModalityCapabilityQueryService modalityCapabilityQueryService;

    @Mock
    private IDomainEventPublisher domainEventPublisher;

    @InjectMocks
    private PacsWorkItemDomainService domainService;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(modalityCapabilityQueryService).assertAllActivitiesCovered(anyList(), any());
    }

    @Test
    void createWorkItem_happyPath_persistsAndPublishesCreatedEvent() {
        CreatePacsWorkItemCommand cmd = new CreatePacsWorkItemCommand();
        cmd.setPatientId(100L);
        cmd.setEncounterId(200L);
        cmd.setOrganizationId(300L);
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);

        CreatePacsOrderLinkItem linkItem = new CreatePacsOrderLinkItem();
        linkItem.setServiceRequestId(501L);
        linkItem.setActivityId(601L);
        cmd.setOrderLinks(List.of(linkItem));

        when(orderRepository.findByServiceRequestId(501L)).thenReturn(Optional.empty());
        when(workItemRepository.save(any(PacsWorkItem.class))).thenAnswer(invocation -> {
            PacsWorkItem entity = invocation.getArgument(0);
            entity.setId(1000L);
            return entity;
        });
        when(orderRepository.save(any(PacsWorkItemOrder.class))).thenAnswer(invocation -> {
            PacsWorkItemOrder order = invocation.getArgument(0);
            order.setId(2000L);
            return order;
        });

        PacsWorkItem result = domainService.createWorkItem(cmd);

        assertNotNull(result);
        assertEquals(1000L, result.getId());
        assertEquals(PacsWorkItemStatus.REGISTERED.getCode(), result.getStatusEnum());

        ArgumentCaptor<PacsWorkItemCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PacsWorkItemCreatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        PacsWorkItemCreatedEvent event = eventCaptor.getValue();
        assertEquals(1000L, event.getAggregateId());
        assertEquals(List.of(501L), event.getServiceRequestIds());
        verify(modalityCapabilityQueryService).assertAllActivitiesCovered(eq(List.of(601L)), eq(1L));
    }

    @Test
    void createLocalWorkItem_happyPath_persistsWithoutOrderLinks() {
        CreateLocalPacsWorkItemCommand cmd = new CreateLocalPacsWorkItemCommand();
        cmd.setPatientId(100L);
        cmd.setEncounterId(200L);
        cmd.setOrganizationId(300L);
        cmd.setTenantId(1L);
        cmd.setModalityCode("CT");
        cmd.setOperatorId(900L);

        when(workItemRepository.save(any(PacsWorkItem.class))).thenAnswer(invocation -> {
            PacsWorkItem entity = invocation.getArgument(0);
            entity.setId(1000L);
            return entity;
        });

        PacsWorkItem result = domainService.createLocalWorkItem(cmd);

        assertNotNull(result);
        assertEquals(1000L, result.getId());
        assertEquals(PacsWorkItemStatus.REGISTERED.getCode(), result.getStatusEnum());

        ArgumentCaptor<PacsWorkItemCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PacsWorkItemCreatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertEquals(List.of(), eventCaptor.getValue().getServiceRequestIds());
        verify(orderRepository, never()).save(any(PacsWorkItemOrder.class));
    }

    @Test
    void completeExam_withoutAccession_throwsRuleConflict() {
        PacsWorkItem workItem = new PacsWorkItem();
        workItem.setId(1000L);
        workItem.setTenantId(1L);
        workItem.initRegistered();

        when(workItemRepository.findById(1000L)).thenReturn(Optional.of(workItem));

        CompletePacsExamCommand cmd = new CompletePacsExamCommand();
        cmd.setWorkItemId(1000L);
        cmd.setOperatorId(900L);

        BusinessException exception = assertThrows(BusinessException.class, () -> domainService.completeExam(cmd));
        assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, exception.getErrorCode());
        verify(domainEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any(DomainEvent.class));
    }
}
