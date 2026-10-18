/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.enums.PacsOrderLinkStatus;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.pacs.domain.port.PacsOrderIngressPort;
import com.zxplat.plugin.pacs.domain.workitem.command.CancelPacsWorkItemCommand;
import com.zxplat.plugin.pacs.domain.workitem.command.CancelPacsWorkItemOrderCommand;
import com.zxplat.plugin.pacs.domain.workitem.command.CreatePacsOrderLinkItem;
import com.zxplat.plugin.pacs.domain.workitem.command.CreatePacsWorkItemCommand;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemOrderQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.ModalityCapabilityQueryService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 检查医嘱接单编排（AS-PACS-INT-001 · INT-01/02/07）——{@link PacsOrderIngressPort} 真实现。
 *
 * <p>由医生站签发/签退经 Port 调用；不经 {@code PacsAdviceApplyExtension}。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class PacsOrderIngressOrchestrator implements PacsOrderIngressPort {

    private static final String WITHDRAW_REASON = "医生站签退";

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ModalityCapabilityQueryService modalityCapabilityQueryService;
    private final PacsWorkItemDomainService pacsWorkItemDomainService;
    private final PacsWorkItemQueryService pacsWorkItemQueryService;

    @Override
    public void assertActivitiesExecutable(List<Long> activityIds) {
        modalityCapabilityQueryService.assertAllActivitiesCovered(activityIds, TenantUtils.getCurrentTenantId());
    }

    @Override
    public Long acceptSignedImagingServiceRequest(
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
        PacsWorkItem saved = pacsWorkItemDomainService.createWorkItem(cmd);
        if (saved == null) {
            return null;
        }
        return saved.getId();
    }

    @Override
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
     * 是否仍有 ACTIVE 的 orderLink。
     *
     * @param detail 工单详情
     * @return true 表示仍有有效 link
     */
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

    /**
     * 签发上下文与 SR 就诊/患者必须一致。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param sr          服务申请读模型
     */
    private void assertEncounterPatientMatch(Long encounterId, Long patientId, ServiceRequestListModel sr) {
        if (!Objects.equals(encounterId, sr.getEncounterId()) || !Objects.equals(patientId, sr.getPatientId())) {
            throw new BusinessException(
                    ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT,
                    "检查医嘱与当前就诊/患者不一致");
        }
    }
}
