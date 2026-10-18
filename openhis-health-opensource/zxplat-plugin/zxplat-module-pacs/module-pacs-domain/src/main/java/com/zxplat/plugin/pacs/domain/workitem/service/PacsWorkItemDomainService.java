/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.service;

import com.zxplat.plugin.pacs.domain.workitem.command.*;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItemOrder;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsWorkItemOrderRepository;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsWorkItemRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.domain.share.events.pacs.workitem.*;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 检查工单（{@code pacs_work_item}）聚合写侧领域服务。
 *
 * <p>职责：接单创建、登记确认、执行完成、作废及媒体标记回写；不写 PHI、不写 DICOM 实体。</p>
 * <p>不变量：见 DM-PACS-001 BR-PWI-01～11；capability 经 {@link ModalityCapabilityQueryService}。</p>
 * <p>仅注入本聚合仓储与模态能力读端口、{@link IDomainEventPublisher}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsWorkItemDomainService {

    private final PacsWorkItemRepository workItemRepository;
    private final PacsWorkItemOrderRepository orderRepository;
    private final ModalityCapabilityQueryService modalityCapabilityQueryService;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建检查工单。
     *
     * @param cmd 创建命令
     * @return 工单聚合根
     */
    public PacsWorkItem createWorkItem(CreatePacsWorkItemCommand cmd) {
        this.validateCreateCommand(cmd);
        // Pre：校验 capability
        List<Long> activityIds = this.collectActivityIds(cmd.getOrderLinks());
        modalityCapabilityQueryService.assertAllActivitiesCovered(activityIds, cmd.getTenantId());
        // Pre：幂等 — 任一 SR 已挂接则返回已有工单
        Optional<PacsWorkItem> idempotent = this.findExistingWorkItemByOrderLinks(cmd.getOrderLinks());
        if (idempotent.isPresent()) {
            return idempotent.get();
        }
        // Cmd：组装聚合根
        PacsWorkItem workItem = new PacsWorkItem();
        workItem.setPatientId(cmd.getPatientId());
        workItem.setEncounterId(cmd.getEncounterId());
        workItem.setOrganizationId(cmd.getOrganizationId());
        workItem.setTenantId(cmd.getTenantId());
        workItem.setModalityCode(cmd.getModalityCode());
        workItem.setBodySiteCode(cmd.getBodySiteCode());
        workItem.setScheduledTime(cmd.getScheduledTime());
        workItem.setCreatedBy(cmd.getOperatorId());
        workItem.setUpdatedBy(cmd.getOperatorId());
        workItem.initRegistered();
        PacsWorkItem savedWorkItem = workItemRepository.save(workItem);
        List<Long> serviceRequestIds = new ArrayList<>();
        for (CreatePacsOrderLinkItem item : cmd.getOrderLinks()) {
            PacsWorkItemOrder order = this.buildActiveOrder(
                savedWorkItem.getId(),
                cmd.getTenantId(),
                item.getServiceRequestId(),
                item.getActivityId(),
                item.getGroupCode(),
                item.getSortNumber(),
                cmd.getOperatorId());
            orderRepository.save(order);
            serviceRequestIds.add(item.getServiceRequestId());
        }
        // 发布事件：工单已创建
        PacsWorkItemCreatedEvent event = new PacsWorkItemCreatedEvent(
            savedWorkItem.getId(),
            savedWorkItem.getPatientId(),
            savedWorkItem.getEncounterId(),
            savedWorkItem.getOrganizationId(),
            savedWorkItem.getStatusEnum(),
            serviceRequestIds);
        event.setTenantId(savedWorkItem.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return savedWorkItem;
    }

    /**
     * 本地补登记创建检查工单（无 ServiceRequest orderLink）。
     *
     * @param cmd 本地创建命令
     * @return 工单聚合根
     */
    public PacsWorkItem createLocalWorkItem(CreateLocalPacsWorkItemCommand cmd) {
        this.validateLocalCreateCommand(cmd);
        if (cmd.getActivityId() != null) {
            List<Long> activityIds = new ArrayList<>();
            activityIds.add(cmd.getActivityId());
            modalityCapabilityQueryService.assertAllActivitiesCovered(activityIds, cmd.getTenantId());
        }
        PacsWorkItem workItem = new PacsWorkItem();
        workItem.setPatientId(cmd.getPatientId());
        workItem.setEncounterId(cmd.getEncounterId());
        workItem.setOrganizationId(cmd.getOrganizationId());
        workItem.setTenantId(cmd.getTenantId());
        workItem.setModalityCode(cmd.getModalityCode());
        workItem.setBodySiteCode(cmd.getBodySiteCode());
        workItem.setDeviceId(cmd.getDeviceId());
        workItem.setScheduledTime(cmd.getScheduledTime());
        workItem.setCreatedBy(cmd.getOperatorId());
        workItem.setUpdatedBy(cmd.getOperatorId());
        workItem.initRegistered();
        PacsWorkItem savedWorkItem = workItemRepository.save(workItem);
        PacsWorkItemCreatedEvent event = new PacsWorkItemCreatedEvent(
            savedWorkItem.getId(),
            savedWorkItem.getPatientId(),
            savedWorkItem.getEncounterId(),
            savedWorkItem.getOrganizationId(),
            savedWorkItem.getStatusEnum(),
            Collections.emptyList());
        event.setTenantId(savedWorkItem.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return savedWorkItem;
    }

    /**
     * 向工单追加医嘱关联。
     *
     * @param cmd 追加命令
     * @return 工单聚合根
     */
    public PacsWorkItem addOrder(AddPacsWorkItemOrderCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：工单可编辑态
        workItem.assertCanEditRegistration();
        // Pre：capability
        List<Long> activityIds = new ArrayList<>();
        activityIds.add(cmd.getActivityId());
        modalityCapabilityQueryService.assertAllActivitiesCovered(activityIds, workItem.getTenantId());
        // Pre：SR 未重复挂接
        if (orderRepository.existsByServiceRequestId(cmd.getServiceRequestId())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "医嘱已关联其他检查工单");
        }
        Optional<PacsWorkItemOrder> duplicated = orderRepository.findByWorkItemIdAndServiceRequestId(
            cmd.getWorkItemId(), cmd.getServiceRequestId());
        if (duplicated.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "工单内医嘱关联已存在");
        }
        // Cmd：插入 order
        PacsWorkItemOrder order = this.buildActiveOrder(
            workItem.getId(),
            workItem.getTenantId(),
            cmd.getServiceRequestId(),
            cmd.getActivityId(),
            cmd.getGroupCode(),
            cmd.getSortNumber(),
            cmd.getOperatorId());
        orderRepository.save(order);
        workItem.setUpdatedBy(cmd.getOperatorId());
        return workItemRepository.save(workItem);
    }

    /**
     * 作废单项医嘱关联。
     *
     * @param cmd 作废命令
     * @return 工单聚合根
     */
    public PacsWorkItem cancelOrder(CancelPacsWorkItemOrderCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：整单仍可撤
        workItem.assertCanCancel();
        PacsWorkItemOrder order = orderRepository.findByWorkItemIdAndServiceRequestId(
                cmd.getWorkItemId(), cmd.getServiceRequestId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "医嘱关联不存在"));
        if (!order.isActiveLink()) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "医嘱关联已作废");
        }
        // Cmd：更新 link
        order.markCancelled();
        order.setUpdatedBy(cmd.getOperatorId());
        orderRepository.save(order);
        workItem.setUpdatedBy(cmd.getOperatorId());
        return workItemRepository.save(workItem);
    }

    /**
     * 整单作废检查工单。
     *
     * @param cmd 作废命令
     * @return 工单聚合根
     */
    public PacsWorkItem cancelWorkItem(CancelPacsWorkItemCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：可作废态
        workItem.assertCanCancel();
        List<PacsWorkItemOrder> orders = orderRepository.findByWorkItemId(cmd.getWorkItemId());
        for (PacsWorkItemOrder order : orders) {
            if (order.isActiveLink()) {
                order.markCancelled();
                order.setUpdatedBy(cmd.getOperatorId());
                orderRepository.save(order);
            }
        }
        // Cmd：作废根
        workItem.markCancelled();
        workItem.setUpdatedBy(cmd.getOperatorId());
        PacsWorkItem saved = workItemRepository.save(workItem);
        // 发布事件：工单已作废
        PacsWorkItemCancelledEvent event = new PacsWorkItemCancelledEvent(
            saved.getId(), cmd.getReason(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 更新登记信息；可选确认进入待摄。
     *
     * @param cmd 更新命令
     * @return 工单聚合根
     */
    public PacsWorkItem updateRegistration(UpdatePacsRegistrationCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：可编辑登记
        workItem.assertCanEditRegistration();
        String previousStatus = workItem.getStatusEnum();
        this.applyRegistrationFields(workItem, cmd);
        boolean scheduleConfirmed = Boolean.TRUE.equals(cmd.getConfirmSchedule());
        if (scheduleConfirmed) {
            String accession = this.resolveAccessionForSchedule(cmd.getAccessionNumber(), workItem);
            workItem.markScheduledWithAccession(accession);
        }
        workItem.setUpdatedBy(cmd.getOperatorId());
        PacsWorkItem saved = workItemRepository.save(workItem);
        if (scheduleConfirmed && PacsWorkItemStatus.REGISTERED.getCode().equals(previousStatus)) {
            this.publishScheduledEvent(saved, cmd.getOperatorId());
        }
        return saved;
    }

    /**
     * 确认登记并进入待摄。
     *
     * @param cmd 确认命令
     * @return 工单聚合根
     */
    public PacsWorkItem confirmRegistration(ConfirmPacsRegistrationCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        if (PacsWorkItemStatus.SCHEDULED.getCode().equals(workItem.getStatusEnum())) {
            workItem.setUpdatedBy(cmd.getOperatorId());
            return workItemRepository.save(workItem);
        }
        String previousStatus = workItem.getStatusEnum();
        // Pre：生成/校验号
        String accession = this.resolveAccessionForSchedule(cmd.getAccessionNumber(), workItem);
        // Cmd：迁状态
        workItem.markScheduledWithAccession(accession);
        workItem.setUpdatedBy(cmd.getOperatorId());
        PacsWorkItem saved = workItemRepository.save(workItem);
        if (PacsWorkItemStatus.REGISTERED.getCode().equals(previousStatus)) {
            this.publishScheduledEvent(saved, cmd.getOperatorId());
        }
        return saved;
    }

    /**
     * 开始检查执行。
     *
     * @param cmd 开始命令
     * @return 工单聚合根
     */
    public PacsWorkItem startExam(StartPacsExamCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：须已有检查号
        workItem.assertHasAccession();
        if (cmd.getPerformerPractitionerId() != null) {
            workItem.setPerformerPractitionerId(cmd.getPerformerPractitionerId());
        }
        if (cmd.getDeviceId() != null) {
            workItem.setDeviceId(cmd.getDeviceId());
        }
        // Cmd：迁入执行中
        workItem.markInProgress();
        workItem.setUpdatedBy(cmd.getOperatorId());
        return workItemRepository.save(workItem);
    }

    /**
     * 确认检查已完成。
     *
     * @param cmd 完成命令
     * @return 工单聚合根
     */
    public PacsWorkItem completeExam(CompletePacsExamCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        // Gate：accession + 合法状态
        workItem.assertCanComplete();
        LocalDateTime performedTime = cmd.getPerformedTime();
        if (performedTime == null) {
            performedTime = LocalDateTime.now();
        }
        if (cmd.getPerformerPractitionerId() != null) {
            workItem.setPerformerPractitionerId(cmd.getPerformerPractitionerId());
        }
        if (cmd.getDeviceId() != null) {
            workItem.setDeviceId(cmd.getDeviceId());
        }
        // Cmd：完成
        workItem.markCompleted(performedTime);
        workItem.setUpdatedBy(cmd.getOperatorId());
        PacsWorkItem saved = workItemRepository.save(workItem);
        // 发布事件：检查已完成
        PacsExamCompletedEvent event = new PacsExamCompletedEvent(
            saved.getId(), saved.getAccessionNumber(), saved.getPerformedTime(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 回写媒体标记。
     *
     * @param cmd 回写命令
     * @return 工单聚合根
     */
    public PacsWorkItem markHasMedia(MarkPacsWorkItemHasMediaCommand cmd) {
        // 加载
        PacsWorkItem workItem = this.loadWorkItem(cmd.getWorkItemId());
        Boolean previous = workItem.getHasMedia();
        if (previous == null) {
            previous = Boolean.FALSE;
        }
        // Cmd：写标志
        workItem.markHasMediaFlag(cmd.getHasMedia());
        workItem.setUpdatedBy(cmd.getOperatorId());
        PacsWorkItem saved = workItemRepository.save(workItem);
        if (!previous.equals(cmd.getHasMedia())) {
            PacsWorkItemMediaFlagUpdatedEvent event = new PacsWorkItemMediaFlagUpdatedEvent(saved.getId(), saved.getHasMedia());
            event.setTenantId(saved.getTenantId());
            event.setUserId(cmd.getOperatorId());
            domainEventPublisher.publish(event);
        }
        return saved;
    }

    private PacsWorkItem loadWorkItem(Long workItemId) {
        return workItemRepository.findById(workItemId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "检查工单不存在"));
    }

    private void validateCreateCommand(CreatePacsWorkItemCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "创建命令不能为空");
        }
        if (cmd.getOrderLinks() == null || cmd.getOrderLinks().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "至少关联一条医嘱");
        }
    }

    /**
     * 校验本地补登创建命令必填项。
     *
     * @param cmd 本地创建命令
     */
    private void validateLocalCreateCommand(CreateLocalPacsWorkItemCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "本地补登命令不能为空");
        }
        if (cmd.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "患者不能为空");
        }
        if (cmd.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "就诊不能为空");
        }
        if (cmd.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "科室不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "租户不能为空");
        }
        if (StringUtils.isEmpty(cmd.getModalityCode())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "模态不能为空");
        }
    }

    private List<Long> collectActivityIds(List<CreatePacsOrderLinkItem> orderLinks) {
        List<Long> activityIds = new ArrayList<>();
        for (CreatePacsOrderLinkItem item : orderLinks) {
            activityIds.add(item.getActivityId());
        }
        return activityIds;
    }

    private Optional<PacsWorkItem> findExistingWorkItemByOrderLinks(List<CreatePacsOrderLinkItem> orderLinks) {
        for (CreatePacsOrderLinkItem item : orderLinks) {
            if (item.getServiceRequestId() == null) {
                continue;
            }
            Optional<PacsWorkItemOrder> existingOrder = orderRepository.findByServiceRequestId(item.getServiceRequestId());
            if (existingOrder.isPresent()) {
                return workItemRepository.findById(existingOrder.get().getWorkItemId());
            }
        }
        return Optional.empty();
    }

    private PacsWorkItemOrder buildActiveOrder(
        Long workItemId,
        Long tenantId,
        Long serviceRequestId,
        Long activityId,
        String groupCode,
        Integer sortNumber,
        Long operatorId) {
        PacsWorkItemOrder order = new PacsWorkItemOrder();
        order.setWorkItemId(workItemId);
        order.setTenantId(tenantId);
        order.setServiceRequestId(serviceRequestId);
        order.setActivityId(activityId);
        order.setGroupCode(groupCode);
        order.setSortNumber(sortNumber);
        order.setCreatedBy(operatorId);
        order.setUpdatedBy(operatorId);
        order.initActive();
        return order;
    }

    private void applyRegistrationFields(PacsWorkItem workItem, UpdatePacsRegistrationCommand cmd) {
        if (cmd.getOrganizationId() != null) {
            workItem.setOrganizationId(cmd.getOrganizationId());
        }
        if (!StringUtils.isEmpty(cmd.getModalityCode())) {
            workItem.setModalityCode(cmd.getModalityCode());
        }
        if (!StringUtils.isEmpty(cmd.getBodySiteCode())) {
            workItem.setBodySiteCode(cmd.getBodySiteCode());
        }
        if (cmd.getDeviceId() != null) {
            workItem.setDeviceId(cmd.getDeviceId());
        }
        if (cmd.getPerformerPractitionerId() != null) {
            workItem.setPerformerPractitionerId(cmd.getPerformerPractitionerId());
        }
        if (cmd.getScheduledTime() != null) {
            workItem.setScheduledTime(cmd.getScheduledTime());
        }
        if (!StringUtils.isEmpty(cmd.getAccessionNumber())) {
            this.assertAccessionUnique(cmd.getAccessionNumber(), workItem.getTenantId(), workItem.getId());
            workItem.setAccessionNumber(cmd.getAccessionNumber());
        }
    }

    private String resolveAccessionForSchedule(String providedAccession, PacsWorkItem workItem) {
        if (!StringUtils.isEmpty(providedAccession)) {
            this.assertAccessionUnique(providedAccession, workItem.getTenantId(), workItem.getId());
            return providedAccession;
        }
        if (!StringUtils.isEmpty(workItem.getAccessionNumber())) {
            return workItem.getAccessionNumber();
        }
        return this.generateAccessionNumber(workItem.getTenantId());
    }

    private void assertAccessionUnique(String accession, Long tenantId, Long excludeWorkItemId) {
        Optional<PacsWorkItem> existing = workItemRepository.findByAccessionNumberAndTenantId(accession, tenantId);
        if (existing.isEmpty()) {
            return;
        }
        if (excludeWorkItemId != null && excludeWorkItemId.equals(existing.get().getId())) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "检查号已存在");
    }

    private String generateAccessionNumber(Long tenantId) {
        String candidate;
        do {
            long suffix = System.currentTimeMillis() * 1000L + ThreadLocalRandom.current().nextInt(1000);
            candidate = "ACC" + suffix;
        } while (workItemRepository.existsByAccessionNumberAndTenantId(candidate, tenantId));
        return candidate;
    }

    private void publishScheduledEvent(PacsWorkItem workItem, Long operatorId) {
        PacsWorkItemScheduledEvent event = new PacsWorkItemScheduledEvent(
            workItem.getId(), workItem.getAccessionNumber(), workItem.getStatusEnum());
        event.setTenantId(workItem.getTenantId());
        event.setUserId(operatorId);
        domainEventPublisher.publish(event);
    }
}
