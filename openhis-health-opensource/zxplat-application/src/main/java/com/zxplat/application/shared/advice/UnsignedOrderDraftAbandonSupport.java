/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.zxplat.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.medication.medicationrequest.command.CancelMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.StaleUnsignedMedicationRequestDraftQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.workflow.devicerequest.command.CancelDeviceOrderDraftCommand;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestQueryModel;
import com.zxplat.domain.workflow.devicerequest.queries.model.StaleUnsignedDeviceRequestDraftQueryModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 未签发药/耗医嘱草稿废弃（DRAFT→CANCELLED，不软删）。
 *
 * <p>供调价保存/提交：仅废弃开立超过 48h 新鲜窗口的草稿；窗口内仅提示、由医生自行删除。
 * 写前再确认仍为未签发 DRAFT，已签发医嘱一律跳过，禁止误伤。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnsignedOrderDraftAbandonSupport {

    private static final String ABANDON_REASON_STALE_FOR_PRICE_ADJUSTMENT =
        "调价保存/提交：开立超过新鲜窗口未签发，系统自动废弃";

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final DoctorStationSkinTestSyncOrchestrator doctorStationSkinTestSyncOrchestrator;
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 废弃单条药品未签发草稿。
     *
     * <p>废弃前再确认仍为未签发 DRAFT；已签发则跳过，避免误伤医嘱。</p>
     *
     * @param draft      草稿读模型
     * @param operatorId 操作人
     * @param reason     取消原因
     */
    public void abandonMedicationDraft(
        StaleUnsignedMedicationRequestDraftQueryModel draft,
        Long operatorId,
        String reason
    ) {
        // 业务分支：空行跳过，避免 NPE
        if (draft == null || draft.getId() == null) {
            return;
        }
        // Gate：写前再读，确认仍是未签发 DRAFT（防列表后医生已签发）
        MedicationRequest current = this.medicationRequestQueryService.findById(draft.getId());
        if (current == null
            || !RequestStatus.DRAFT.getCode().equals(current.getStatusEnum())
            || current.getReqAuthoredTime() != null) {
            log.info("跳过废弃药品草稿（已非未签发 DRAFT），requestId={}", draft.getId());
            return;
        }
        // Cmd：作废锚定本药嘱的未计费草稿费用
        this.chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
            ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                draft.getEncounterId(),
                TableNameConstants.MED_MEDICATION_REQUEST,
                draft.getId(),
                operatorId));
        // Cmd：同步清理草稿态皮试 companion（门诊/住院按就诊类别选挂费策略）
        this.doctorStationSkinTestSyncOrchestrator.removeDraftCompanionSkinTestServiceRequest(
            this.resolveSkinTestChargePolicy(draft.getEncounterId()),
            draft.getEncounterId(),
            draft.getId(),
            operatorId);
        // Cmd：仅未签发 DRAFT→CANCELLED（不软删；已签发静默跳过）
        CancelMedicationOrderCommand cancelCmd = new CancelMedicationOrderCommand();
        cancelCmd.setId(draft.getId());
        cancelCmd.setOperatorId(operatorId);
        cancelCmd.setCancelReason(reason);
        this.medicationRequestDomainService.cancelUnsignedMedicationOrderDraft(cancelCmd);
    }

    /**
     * 废弃单条耗材未签发草稿。
     *
     * <p>废弃前再确认仍为 DRAFT；已签发则跳过，避免误伤医嘱。</p>
     *
     * @param draft      草稿读模型
     * @param operatorId 操作人
     * @param reason     取消原因
     */
    public void abandonDeviceDraft(
        StaleUnsignedDeviceRequestDraftQueryModel draft,
        Long operatorId,
        String reason
    ) {
        // 业务分支：空行跳过
        if (draft == null || draft.getId() == null) {
            return;
        }
        // Gate：写前再读，确认仍是草稿（防列表后医生已签发）
        DeviceRequestQueryModel current = this.deviceRequestQueryService.mustGetDeviceRequest(draft.getId());
        if (current == null || !RequestStatus.DRAFT.getCode().equals(current.getStatusEnum())) {
            log.info("跳过废弃耗材草稿（已非 DRAFT），requestId={}", draft.getId());
            return;
        }
        // Cmd：作废锚定本耗材申请的未计费草稿费用
        this.chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
            ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                draft.getEncounterId(),
                TableNameConstants.WKF_DEVICE_REQUEST,
                draft.getId(),
                operatorId));
        // Cmd：DRAFT→CANCELLED（不软删；已非草稿静默跳过）
        CancelDeviceOrderDraftCommand cancelCmd = new CancelDeviceOrderDraftCommand();
        cancelCmd.setId(draft.getId());
        cancelCmd.setOperatorId(operatorId);
        cancelCmd.setCancelReason(reason);
        this.deviceRequestDomainService.cancelDeviceOrderDraft(cancelCmd);
    }

    /**
     * 按调价单物流行自动废弃「超过新鲜窗口」的未签发药/耗草稿（静默，不提示）。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 废弃条数
     */
    public int abandonStaleUnsignedDraftsByPriceAdjustmentBusNo(String busNo, Long operatorId) {
        // 业务分支：空单号无法定位单据
        if (StringUtils.isEmpty(busNo)) {
            return 0;
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        return this.abandonStaleUnsignedDraftsForLines(document.getLines(), operatorId);
    }

    /**
     * 按调价项目行自动废弃超过新鲜窗口的未签发药/耗草稿。
     *
     * @param lines      调价项目行
     * @param operatorId 操作人
     * @return 废弃条数
     */
    public int abandonStaleUnsignedDraftsForLines(List<PriceAdjustmentLineQueryModel> lines, Long operatorId) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        LocalDateTime freshAfter = LocalDateTime.now()
            .minusHours(CustomConstants.PriceAdjustment.UNSIGNED_DRAFT_FRESH_HOURS);
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        this.collectLogisticsItemIds(lines, medicationIds, deviceDefIds);
        int abandoned = 0;
        for (Long medicationId : medicationIds) {
            List<StaleUnsignedMedicationRequestDraftQueryModel> drafts =
                this.medicationRequestQueryService.listUnsignedDraftsByMedicationId(medicationId);
            for (StaleUnsignedMedicationRequestDraftQueryModel draft : drafts) {
                // 业务分支：仍在 48h 新鲜窗口内 → 不自动废弃
                if (this.isFreshDraft(draft.getOpenedAt(), freshAfter)) {
                    continue;
                }
                this.abandonMedicationDraft(draft, operatorId, ABANDON_REASON_STALE_FOR_PRICE_ADJUSTMENT);
                abandoned++;
            }
        }
        for (Long deviceDefId : deviceDefIds) {
            List<StaleUnsignedDeviceRequestDraftQueryModel> drafts =
                this.deviceRequestQueryService.listUnsignedDraftsByDeviceDefId(deviceDefId);
            for (StaleUnsignedDeviceRequestDraftQueryModel draft : drafts) {
                if (this.isFreshDraft(draft.getOpenedAt(), freshAfter)) {
                    continue;
                }
                this.abandonDeviceDraft(draft, operatorId, ABANDON_REASON_STALE_FOR_PRICE_ADJUSTMENT);
                abandoned++;
            }
        }
        return abandoned;
    }

    /**
     * 收集调价单物流行上仍处新鲜窗口的未签发草稿（仅本聚合字段，展示名由调用方 enrich）。
     *
     * @param busNo 调价单号
     * @return 新鲜草稿读模型列表（药+耗）
     */
    public List<StaleUnsignedMedicationRequestDraftQueryModel> listFreshMedicationDraftsByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return new ArrayList<>();
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        return this.listFreshMedicationDraftsForLines(document.getLines());
    }

    /**
     * 按项目行收集新鲜窗口内药品未签发草稿。
     *
     * @param lines 调价项目行
     * @return 新鲜药品草稿
     */
    public List<StaleUnsignedMedicationRequestDraftQueryModel> listFreshMedicationDraftsForLines(
        List<PriceAdjustmentLineQueryModel> lines
    ) {
        List<StaleUnsignedMedicationRequestDraftQueryModel> result = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        LocalDateTime freshAfter = LocalDateTime.now()
            .minusHours(CustomConstants.PriceAdjustment.UNSIGNED_DRAFT_FRESH_HOURS);
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        this.collectLogisticsItemIds(lines, medicationIds, deviceDefIds);
        for (Long medicationId : medicationIds) {
            List<StaleUnsignedMedicationRequestDraftQueryModel> drafts =
                this.medicationRequestQueryService.listUnsignedDraftsByMedicationId(medicationId);
            for (StaleUnsignedMedicationRequestDraftQueryModel draft : drafts) {
                if (this.isFreshDraft(draft.getOpenedAt(), freshAfter)) {
                    result.add(draft);
                }
            }
        }
        return result;
    }

    /**
     * 按项目行收集新鲜窗口内耗材未签发草稿。
     *
     * @param lines 调价项目行
     * @return 新鲜耗材草稿
     */
    public List<StaleUnsignedDeviceRequestDraftQueryModel> listFreshDeviceDraftsForLines(
        List<PriceAdjustmentLineQueryModel> lines
    ) {
        List<StaleUnsignedDeviceRequestDraftQueryModel> result = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        LocalDateTime freshAfter = LocalDateTime.now()
            .minusHours(CustomConstants.PriceAdjustment.UNSIGNED_DRAFT_FRESH_HOURS);
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        this.collectLogisticsItemIds(lines, medicationIds, deviceDefIds);
        for (Long deviceDefId : deviceDefIds) {
            List<StaleUnsignedDeviceRequestDraftQueryModel> drafts =
                this.deviceRequestQueryService.listUnsignedDraftsByDeviceDefId(deviceDefId);
            for (StaleUnsignedDeviceRequestDraftQueryModel draft : drafts) {
                if (this.isFreshDraft(draft.getOpenedAt(), freshAfter)) {
                    result.add(draft);
                }
            }
        }
        return result;
    }

    /**
     * 按调价单号收集新鲜窗口内耗材未签发草稿。
     *
     * @param busNo 调价单号
     * @return 新鲜耗材草稿
     */
    public List<StaleUnsignedDeviceRequestDraftQueryModel> listFreshDeviceDraftsByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return new ArrayList<>();
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        return this.listFreshDeviceDraftsForLines(document.getLines());
    }

    /**
     * 从调价行收集药/耗定义主键（仅物流表）。
     *
     * @param lines          项目行
     * @param medicationIds  药品定义收集器
     * @param deviceDefIds   耗材定义收集器
     */
    private void collectLogisticsItemIds(
        List<PriceAdjustmentLineQueryModel> lines,
        Set<Long> medicationIds,
        Set<Long> deviceDefIds
    ) {
        for (PriceAdjustmentLineQueryModel line : lines) {
            // 业务分支：空行或非物流表跳过
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(line.getItemTable())) {
                continue;
            }
            String itemTable = line.getItemTable().trim();
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
                medicationIds.add(line.getItemId());
                continue;
            }
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
                deviceDefIds.add(line.getItemId());
            }
        }
    }

    /**
     * 是否仍在新鲜窗口内（openedAt 为空视为已过期，允许自动废弃）。
     *
     * @param openedAt   有效开立时刻
     * @param freshAfter 新鲜窗口下界（早于此则过期）
     * @return 新鲜时 true
     */
    private boolean isFreshDraft(LocalDateTime openedAt, LocalDateTime freshAfter) {
        // 业务分支：无开立时刻按过期处理，避免脏数据永远挡调价
        if (openedAt == null || freshAfter == null) {
            return false;
        }
        return !openedAt.isBefore(freshAfter);
    }

    /**
     * 按就诊类别解析皮试 companion 挂费策略（住院不挂门诊操作费）。
     *
     * @param encounterId 就诊主键；空则按门诊策略兜底
     * @return 门诊或住院挂费策略
     */
    private DoctorStationSkinTestSyncOrchestrator.ChargePolicy resolveSkinTestChargePolicy(Long encounterId) {
        // 业务分支：无就诊时按门诊策略兜底
        if (encounterId == null) {
            return DoctorStationSkinTestSyncOrchestrator.ChargePolicy.OUTPATIENT;
        }
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(encounterId);
        // 业务分支：住院就诊用住院策略
        if (EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
            return DoctorStationSkinTestSyncOrchestrator.ChargePolicy.INPATIENT;
        }
        return DoctorStationSkinTestSyncOrchestrator.ChargePolicy.OUTPATIENT;
    }
}
