/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inpatient;

import com.zxplat.application.shared.advice.InpatientOrderLookupLoader;
import com.zxplat.domain.administration.encounter.command.RevertTransferOrDischargeHoldCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.custom.encounterhistory.command.RetireMilestoneCommand;
import com.zxplat.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.zxplat.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.zxplat.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.application.shared.advice.ServiceAdviceCommandAssembler;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 转科/出院 {@code ON_HOLD} 正向/逆向就诊编排（GAP-ATD-006/008）。
 *
 * <p>供住院医生站医嘱撤销/删草稿与护士站校对退回共用，避免父级 {@code inpatient.support} 落点。</p>
 * <p>范围：仅办理出院前的 {@code ON_HOLD}（待转科/待出院）。已办理出院的 {@code PENDING} 另轨，本编排不回滚。</p>
 * <p>逆向优先按 {@code service_request.id} 对齐里程碑；对不齐时仍回滚就诊态，避免 {@code ON_HOLD}
 * 悬挂导致无法开立医嘱、无法改计划出院时间。</p>
 * <p>出院医嘱内容不可行内修订：退回/签退至草稿后由本编排软删，医生须右上角重开（AS-IP-DOR-003 /
 * AS-IP-NUR-003）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientTransferDischargeHoldOrchestrator {

    /** 诊疗申请读侧查询（转科/出院类别与已校对残留判定）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 诊疗申请写侧（出院退回/签退后软删草稿）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 就诊读侧查询（住院类与 ON_HOLD 状态校验）。 */
    private final EncounterQueryService encounterQueryService;
    /** 就诊里程碑读侧查询（按 requestId 对齐挂起医嘱）。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    /** 就诊里程碑写侧（回滚成功后废弃本条转科/出院里程碑，避免脏首条干扰）。 */
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    /** 就诊写侧领域服务（转科/出院挂起逆向回滚）。 */
    private final EncounterDomainService encounterDomainService;
    /** 住院医嘱 → 就诊主键解析（读模型 order_type 分流；本编排优先用列表模型 encounterId）。 */
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;

    /**
     * 诊疗撤销（退回/签退/停嘱/删草稿）后，若其为转科/出院且就诊仍 {@code ON_HOLD}、无其它已校对残留，
     * 则回滚就诊至 {@code IN_PROGRESS}（取消校对还原在科，GAP-ATD-008）。
     *
     * <p>业务口径：校对出院 → 待出院；取消校对 = 还原，医嘱回草稿、就诊回在科；办理出院后的 {@code PENDING} 不回滚。</p>
     * <p>里程碑 {@code requestId} 优先对齐；对不齐时仍回滚就诊态并按 requestId 扫废里程碑，避免 {@code ON_HOLD} 悬挂。</p>
     *
     * @param serviceRequestId  刚撤销的诊疗申请主键
     * @param operatorId        操作人
     * @param revertedAt        撤销时刻
     * @param conflictErrorCode 并发/状态冲突映射（如 EX-IP-AUD-203）
     */
    public void tryRevertHoldAfterServiceRequestReverted(
        Long serviceRequestId,
        Long operatorId,
        LocalDateTime revertedAt,
        String conflictErrorCode) {
        // Gate：空主键幂等返回，调用方可无条件挂载
        if (serviceRequestId == null) {
            return;
        }
        try {
            // Pre：用列表模型拿 category + encounterId（QueryModel 无 encounterId）
            ServiceRequestListModel head = serviceRequestQueryService.getListModelById(serviceRequestId);
            // 申请已软删或不存在：无法再编排，幂等结束
            if (head == null) {
                return;
            }
            String category = head.getCategoryEnum();
            boolean isTransfer = InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(category);
            boolean isDischarge = InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(category);
            // 非转科/出院类诊疗撤销不参与 ON_HOLD 回滚，避免误改就诊状态
            if (!isTransfer && !isDischarge) {
                return;
            }
            Long encounterId = head.getEncounterId();
            // 列表模型缺 encounterId 时回落 lookup（极少见脏数据）
            if (encounterId == null) {
                encounterId = inpatientOrderLookupLoader.resolveEncounterId(
                    ReadModelOrderTypeConstants.SERVICE_REQUEST, serviceRequestId);
            }
            // 仍无法解析就诊：无法回滚，避免误抛
            if (encounterId == null) {
                return;
            }
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                conflictErrorCode,
                "仅住院就诊允许转科/出院联动");
            // 已办理出院进入 PENDING 等非 ON_HOLD：本编排不回滚（取消出院/反结另轨）
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                return;
            }
            // 仍有其它已校对的转科/出院医嘱时，就诊须保持 ON_HOLD
            if (serviceRequestQueryService.hasVerifiedTransferOrDischargeRequest(encounterId, serviceRequestId)) {
                return;
            }
            // Pre：优先对齐本条 requestId 的里程碑；对不齐仍回滚，避免历史脏数据导致 ON_HOLD 悬挂
            EncounterHistoryMilestoneQueryModel alignedMilestone =
                this.findAlignedTransferOrDischargeMilestone(encounterId, isTransfer, serviceRequestId);
            RevertTransferOrDischargeHoldCommand command = new RevertTransferOrDischargeHoldCommand();
            command.setOperatorId(operatorId);
            RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand item =
                new RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand();
            item.setEncounterId(encounterId);
            item.setTransferHold(isTransfer);
            item.setServiceRequestId(serviceRequestId);
            item.setRevertedAt(revertedAt);
            command.setItems(Collections.singletonList(item));
            // Cmd：ON_HOLD → IN_PROGRESS，出院轨清空 outWayCode（取消校对还原「在科」）
            encounterDomainService.revertTransferOrDischargeHold(command);
            // Cmd：废弃对齐里程碑；无对齐时按 requestId 扫废弃，防止脏首条干扰再次开立
            if (alignedMilestone != null) {
                this.retireAlignedMilestone(alignedMilestone.getId(), operatorId);
            } else {
                this.retireMilestonesByRequestId(encounterId, isTransfer, serviceRequestId, operatorId);
            }
        } catch (BusinessException ex) {
            // 领域状态冲突映射为调用方场景码（护士 EX-IP-AUD-203 / 医生 ENCOUNTER_INVALID_STATUS）
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(conflictErrorCode, "患者流转状态已变更，请刷新后重试");
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(conflictErrorCode, "患者流转状态已变更，请刷新后重试");
        }
    }

    /**
     * 出院医嘱退回/签退至草稿后：废弃离院里程碑并软删申请。
     *
     * <p>出院行表单字段不适用行内编辑，产品口径为「删后右上角重开」。调用方须已完成
     * {@code rejectServiceRequestToDraft}/{@code withdrawSignedServiceRequest}，并已调用
     * {@link #tryRevertHoldAfterServiceRequestReverted}（待出院 ON_HOLD 回滚）。</p>
     * <p>未进 ON_HOLD 的 ACTIVE 退回/签退时 {@link #tryRevertHoldAfterServiceRequestReverted}
     * 不会废里程碑，本方法按 requestId 补废，避免脏离院里程碑干扰再次开立。</p>
     * <p>非出院类或申请已不可见时幂等返回。</p>
     *
     * @param serviceRequestId 已退回草稿的出院诊疗申请主键
     * @param operatorId       操作人
     */
    public void softDeleteDischargeAdviceAfterRevertedToDraft(Long serviceRequestId, Long operatorId) {
        // Gate：空主键幂等，调用方可无条件挂载
        if (serviceRequestId == null) {
            return;
        }
        // Pre：读列表模型判定类目；已软删则结束
        ServiceRequestListModel head = serviceRequestQueryService.getListModelById(serviceRequestId);
        if (head == null) {
            return;
        }
        // 仅出院类（category=34）走「退回即删」；转科仍保留草稿供修订
        if (!InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(head.getCategoryEnum())) {
            return;
        }
        Long encounterId = head.getEncounterId();
        // Pre：缺 encounterId 时回落 lookup，保证仍能按 requestId 扫废里程碑
        if (encounterId == null) {
            encounterId = inpatientOrderLookupLoader.resolveEncounterId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, serviceRequestId);
        }
        // Cmd：补废未进 ON_HOLD 场景下残留的离院里程碑（tryRevertHold 已废则读侧为空，循环无操作）
        if (encounterId != null) {
            this.retireMilestonesByRequestId(encounterId, false, serviceRequestId, operatorId);
        }
        // Cmd：软删出院草稿主档与明细，开放同就诊再次 IF-IP-DOR-21 开立
        serviceRequestDomainService.removeServiceRequestDraft(
            ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(serviceRequestId, operatorId));
    }

    /**
     * 按就诊 + 里程碑类型 + 当前诊疗申请主键定位转科/出院里程碑。
     *
     * <p>开立出院/转科时即写入里程碑；同就诊可有多条历史行。逆向与幂等校对须
     * {@code requestId} 精确对齐，不得取 {@code createdAt} 升序首条。</p>
     *
     * @param encounterId      就诊主键
     * @param transfer         true=转科里程碑，false=出院里程碑
     * @param serviceRequestId 当前操作的诊疗申请主键
     * @return 对齐里程碑；未命中 null
     */
    private EncounterHistoryMilestoneQueryModel findAlignedTransferOrDischargeMilestone(
        Long encounterId,
        boolean transfer,
        Long serviceRequestId) {
        // Gate：主键不全则无法对齐
        if (encounterId == null || serviceRequestId == null) {
            return null;
        }
        String milestoneName = this.resolveMilestoneName(transfer);
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            // 仅同名里程碑（转科/出院）参与对齐
            if (!milestoneName.equals(milestone.getName())) {
                continue;
            }
            // 仅认本条医嘱开立时写入的里程碑
            if (serviceRequestId.equals(milestone.getRequestId())) {
                return milestone;
            }
        }
        return null;
    }

    /**
     * 无精确对齐行时，按 requestId 废弃同名转科/出院里程碑（回滚悬挂后的清脏）。
     *
     * @param encounterId      就诊主键
     * @param transfer         true=转科
     * @param serviceRequestId 诊疗申请主键
     * @param operatorId       操作人
     */
    private void retireMilestonesByRequestId(
        Long encounterId,
        boolean transfer,
        Long serviceRequestId,
        Long operatorId) {
        // Gate：主键不全则无可废里程碑
        if (encounterId == null || serviceRequestId == null) {
            return;
        }
        String milestoneName = this.resolveMilestoneName(transfer);
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null || milestone.getId() == null) {
                continue;
            }
            // 仅同名且 requestId 命中的行参与清脏
            if (!milestoneName.equals(milestone.getName())) {
                continue;
            }
            if (!serviceRequestId.equals(milestone.getRequestId())) {
                continue;
            }
            this.retireAlignedMilestone(milestone.getId(), operatorId);
        }
    }

    /**
     * 转科/出院里程碑 name 常量。
     *
     * @param transfer true=转科
     * @return 里程碑 name
     */
    private String resolveMilestoneName(boolean transfer) {
        // 转科与出院分属不同里程碑 name，禁止混用
        if (transfer) {
            return CustomConstants.EncounterHistoryMilestoneName.TRANSFER;
        }
        return CustomConstants.EncounterHistoryMilestoneName.DISCHARGE;
    }

    /**
     * 回滚 ON_HOLD 成功后废弃对齐里程碑（软删，读侧过滤器不再返回）。
     *
     * @param encounterHistoryId 里程碑主键
     * @param operatorId         操作人
     */
    private void retireAlignedMilestone(Long encounterHistoryId, Long operatorId) {
        // Gate：空主键幂等
        if (encounterHistoryId == null) {
            return;
        }
        RetireMilestoneCommand retireCommand = new RetireMilestoneCommand();
        retireCommand.setEncounterHistoryId(encounterHistoryId);
        retireCommand.setOperatorId(operatorId);
        retireCommand.setReason(InpatientNurseStationConstants.MSG_RETIRE_TRANSFER_DISCHARGE_MILESTONE_ON_HOLD_REVERT);
        encounterHistoryDomainService.retireMilestone(retireCommand);
    }
}
