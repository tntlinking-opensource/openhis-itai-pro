/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.support;

import com.openhis.health.application.shared.advice.ServiceAdviceCommandAssembler;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtDomainService;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisExamHistoryConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.command.CancelServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 体检历史：未收费取消（作废就诊、清未结费用、回退免收费团检名册）。
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamHistoryCancelExecutor {

    private final EncounterQueryService encounterQueryService;
    private final EncounterDomainService encounterDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final PeisServiceRequestExtDomainService peisServiceRequestExtDomainService;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupCheckDomainService peisGroupCheckDomainService;

    /**
     * 取消未收费体检。
     *
     * @param encounterId 就诊主键
     * @param operatorId  操作人（医务人员主键）
     */
    public void cancel(Long encounterId, Long operatorId) {
        Encounter encounter = encounterQueryService.requireEncounterEntityOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_HIST_INVALID_ARGUMENT,
            PeisExamHistoryConstants.MSG_NOT_PE);
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_HIST_INVALID_STATUS, PeisExamHistoryConstants.MSG_NOT_IN_PROGRESS);
        }
        Optional<DiagnosticReportModel> reportOpt = diagnosticReportQueryService.getByEncounterId(encounterId);
        if (reportOpt.isPresent()
            && DiagnosticReportStatusConstants.STATUS_FINAL.equals(reportOpt.get().getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_HIST_INVALID_STATUS, PeisExamHistoryConstants.MSG_FINAL);
        }
        if (hasBlockingCharge(encounterId)) {
            throw new BusinessException(ErrorCodeConstants.PEIS_HIST_PAID, PeisExamHistoryConstants.MSG_PAID);
        }
        Optional<PeisGroupRoster> rosterOpt = peisGroupCheckQueryService.findRosterByEncounterId(encounterId);
        PeisGroupRoster roster = null;
        if (rosterOpt.isPresent()) {
            roster = rosterOpt.get();
            if (PeisGroupRosterConstants.PAY_PAID.equals(roster.getPayStatusCode())) {
                throw new BusinessException(
                    ErrorCodeConstants.PEIS_HIST_PAID, PeisExamHistoryConstants.MSG_GROUP_PAID);
            }
        }
        List<ServiceRequest> requests = serviceRequestQueryService.listByEncounterIds(List.of(encounterId));
        List<Long> srIds = new ArrayList<>();
        for (ServiceRequest request : requests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            srIds.add(request.getId());
        }
        if (!srIds.isEmpty()) {
            // 加项仍锚 SR，取消时先清子项未结费用
            chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, srIds);
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, srIds);
        }
        // 个检套餐整单锚 AD、团检套餐锚合同，取消时一并清未结费用
        this.voidUnbilledNonServiceRequestCharges(encounterId, operatorId);
        for (ServiceRequest request : requests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            cancelOrRemoveServiceRequest(request, operatorId);
            peisServiceRequestExtDomainService.removeForServiceRequest(request.getId(), operatorId);
        }
        if (roster != null && PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            peisGroupCheckDomainService.cancelArrival(roster.getId(), operatorId);
        }
        encounterDomainService.voidPhysicalExamEncounter(encounterId);
        if (roster != null && PeisGroupRosterConstants.PAY_ENROLLED.equals(roster.getPayStatusCode())) {
            peisGroupCheckDomainService.revertEnrollment(roster.getId(), operatorId);
        }
    }

    private boolean hasBlockingCharge(Long encounterId) {
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        for (ChargeItem item : items) {
            if (item == null) {
                continue;
            }
            String status = item.getStatusEnum();
            if (ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 作废非 SR 锚点上尚未结算的收费（个检套餐 AD / 团检合同）。
     *
     * @param encounterId 就诊主键
     * @param operatorId  操作人
     */
    private void voidUnbilledNonServiceRequestCharges(Long encounterId, Long operatorId) {
        if (encounterId == null || operatorId == null) {
            // 缺就诊或操作人无法按锚点作废
            return;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        Set<String> seenAnchors = new HashSet<>();
        for (ChargeItem item : items) {
            if (item == null || item.getServiceId() == null || StringUtils.isEmpty(item.getServiceTable())) {
                continue;
            }
            // 子项 SR 已按申请批量清理
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(item.getServiceTable())) {
                continue;
            }
            String anchorKey = item.getServiceTable() + "#" + item.getServiceId();
            // 同一锚点只回滚/作废一次
            if (!seenAnchors.add(anchorKey)) {
                continue;
            }
            chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                encounterId, item.getServiceTable(), item.getServiceId());
            VoidUnbilledDraftChargeItemsByServiceAnchorCommand voidCmd =
                new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
            voidCmd.setOperatorId(operatorId);
            voidCmd.setEncounterId(encounterId);
            voidCmd.setServiceTable(item.getServiceTable());
            voidCmd.setServiceId(item.getServiceId());
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(voidCmd);
        }
    }

    private void cancelOrRemoveServiceRequest(ServiceRequest request, Long operatorId) {
        String status = request.getStatusEnum();
        if (RequestStatus.CANCELLED.getCode().equals(status)) {
            return;
        }
        Long srId = request.getId();
        if (RequestStatus.DRAFT.getCode().equals(status)) {
            serviceRequestDomainService.removeServiceRequestDraft(
                ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(srId, operatorId));
            return;
        }
        if (RequestStatus.ACTIVE.getCode().equals(status)
            || RequestStatus.VERIFIED.getCode().equals(status)
            || RequestStatus.COMPLETED.getCode().equals(status)) {
            CancelServiceRequestCommand cancelCmd = new CancelServiceRequestCommand();
            cancelCmd.setOperatorId(operatorId);
            CancelServiceRequestCommand.CancelServiceRequestItemCommand item =
                new CancelServiceRequestCommand.CancelServiceRequestItemCommand();
            item.setId(srId);
            item.setCancelReason(PeisExamHistoryConstants.CANCEL_REASON);
            cancelCmd.setItem(item);
            serviceRequestDomainService.cancelServiceRequest(cancelCmd);
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.PEIS_HIST_INVALID_STATUS, PeisExamHistoryConstants.MSG_SR_NOT_CANCELABLE);
    }
}
