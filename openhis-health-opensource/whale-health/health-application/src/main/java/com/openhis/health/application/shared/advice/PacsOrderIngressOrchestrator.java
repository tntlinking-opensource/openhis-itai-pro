/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.pacs.workitem.command.CancelPacsWorkItemCommand;
import com.openhis.health.domain.pacs.workitem.command.CancelPacsWorkItemOrderCommand;
import com.openhis.health.domain.pacs.workitem.command.CreatePacsOrderLinkItem;
import com.openhis.health.domain.pacs.workitem.command.CreatePacsWorkItemCommand;
import com.openhis.health.domain.pacs.workitem.entity.PacsWorkItem;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemOrderQueryModel;
import com.openhis.health.domain.pacs.workitem.service.ModalityCapabilityQueryService;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemDomainService;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.PacsOrderLinkStatus;
import com.openhis.health.domain.share.enums.PacsWorkItemStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 检查医嘱接单编排（AS-PACS-INT-001 · INT-01/02/03/07）。
 *
 * <p>医生站签发/签退后直调本编排 → {@link PacsWorkItemDomainService}；
 * 不经 {@code PacsAdviceApplyExtension}。</p>
 */
@Component
@RequiredArgsConstructor
public class PacsOrderIngressOrchestrator {

    private static final String WITHDRAW_REASON = "医生站签退";

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ModalityCapabilityQueryService modalityCapabilityQueryService;
    private final PacsWorkItemDomainService pacsWorkItemDomainService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;

    /**
     * IF-PACS-INT-07：断言 activity 均已配置启用态模态能力（缺失则拒绝）。
     *
     * @param activityIds ActivityDefinition 主键列表
     */
    public void assertActivitiesExecutable(List<Long> activityIds) {
        modalityCapabilityQueryService.assertAllActivitiesCovered(activityIds, TenantUtils.getCurrentTenantId());
    }

    /**
     * IF-PACS-INT-01：若已签发的 ServiceRequest 为检查类，则创建（或幂等返回）PacsWorkItem。
     *
     * @param encounterId 就诊主键（签发上下文）
     * @param patientId 患者主键（签发上下文）
     * @param serviceRequestId ServiceRequest 主键
     * @param fallbackOrganizationId 签发科室（SR.orgId 为空时回落）
     * @param operatorId 操作人
     * @return 新建或已有工单；非检查类返回 null
     */
    public PacsWorkItem acceptSignedImagingServiceRequest(
        Long encounterId,
        Long patientId,
        Long serviceRequestId,
        Long fallbackOrganizationId,
        Long operatorId) {
        // Gate：加载 SR
        ServiceRequestListModel sr = serviceRequestQueryService.mustGetListModelById(serviceRequestId);
        // Gate：仅检查类（category=23）
        if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST.equals(sr.getCategoryEnum())) {
            return null;
        }
        // Pre：上下文与 SR 锚点一致
        this.assertEncounterPatientMatch(encounterId, patientId, sr);
        if (sr.getActivityId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "检查医嘱缺少 activityId");
        }
        Long organizationId = sr.getOrgId() != null ? sr.getOrgId() : fallbackOrganizationId;
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "检查工单缺少执行科室");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：可执行性（INT-07）+ 解析模态
        String modalityCode = modalityCapabilityQueryService.resolveModalityCode(sr.getActivityId(), tenantId);
        // Pre：组装命令（P0：一 SR 一工单）
        CreatePacsOrderLinkItem link = new CreatePacsOrderLinkItem();
        link.setServiceRequestId(sr.getId());
        link.setActivityId(sr.getActivityId());
        link.setSortNumber(sr.getSortNumber());
        CreatePacsWorkItemCommand cmd = new CreatePacsWorkItemCommand();
        cmd.setPatientId(patientId);
        cmd.setEncounterId(encounterId);
        cmd.setOrganizationId(organizationId);
        cmd.setTenantId(tenantId);
        cmd.setModalityCode(modalityCode);
        cmd.setOperatorId(operatorId);
        cmd.setOrderLinks(List.of(link));
        // Cmd：写库
        return pacsWorkItemDomainService.createWorkItem(cmd);
    }

    /**
     * IF-PACS-INT-02：医生站签退后作废对应 orderLink；无有效 link 时整单作废。
     *
     * <p>无关联工单时静默跳过（非检查类 / 历史未接单数据）。</p>
     *
     * @param serviceRequestId ServiceRequest 主键
     * @param operatorId 操作人
     */
    public void withdrawSignedImagingServiceRequest(Long serviceRequestId, Long operatorId) {
        Optional<PacsWorkItemDetailQueryModel> detailOptional =
            pacsWorkItemQueryService.findOptionalByServiceRequestId(serviceRequestId);
        if (detailOptional.isEmpty()) {
            return;
        }
        PacsWorkItemDetailQueryModel detail = detailOptional.get();
        // 已整单作废：幂等
        if (PacsWorkItemStatus.ENTERED_IN_ERROR.getCode().equals(detail.getStatusEnum())) {
            return;
        }
        CancelPacsWorkItemOrderCommand cancelOrderCmd = new CancelPacsWorkItemOrderCommand();
        cancelOrderCmd.setWorkItemId(detail.getId());
        cancelOrderCmd.setServiceRequestId(serviceRequestId);
        cancelOrderCmd.setReason(WITHDRAW_REASON);
        cancelOrderCmd.setOperatorId(operatorId);
        pacsWorkItemDomainService.cancelOrder(cancelOrderCmd);
        // BR-INT-05：无剩余有效 link → 整单作废
        PacsWorkItemDetailQueryModel after = pacsWorkItemQueryService.getDetail(detail.getId());
        if (!this.hasActiveOrderLink(after)) {
            CancelPacsWorkItemCommand cancelWorkItemCmd = new CancelPacsWorkItemCommand();
            cancelWorkItemCmd.setWorkItemId(detail.getId());
            cancelWorkItemCmd.setReason(WITHDRAW_REASON);
            cancelWorkItemCmd.setOperatorId(operatorId);
            pacsWorkItemDomainService.cancelWorkItem(cancelWorkItemCmd);
        }
    }

    /**
     * IF-PACS-INT-03：按 ServiceRequest 查询检查工单摘要态。
     *
     * @param serviceRequestId ServiceRequest 主键
     * @return 详情；无关联时 empty
     */
    public Optional<PacsWorkItemDetailQueryModel> findStatusByServiceRequestId(Long serviceRequestId) {
        return pacsWorkItemQueryService.findOptionalByServiceRequestId(serviceRequestId);
    }

    private boolean hasActiveOrderLink(PacsWorkItemDetailQueryModel detail) {
        if (detail == null || detail.getOrderLinks() == null) {
            return false;
        }
        for (PacsWorkItemOrderQueryModel link : detail.getOrderLinks()) {
            if (link != null && PacsOrderLinkStatus.ACTIVE.getCode().equals(link.getLinkStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    private void assertEncounterPatientMatch(Long encounterId, Long patientId, ServiceRequestListModel sr) {
        if (!Objects.equals(encounterId, sr.getEncounterId()) || !Objects.equals(patientId, sr.getPatientId())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT,
                "检查医嘱与当前就诊/患者不一致");
        }
    }
}
