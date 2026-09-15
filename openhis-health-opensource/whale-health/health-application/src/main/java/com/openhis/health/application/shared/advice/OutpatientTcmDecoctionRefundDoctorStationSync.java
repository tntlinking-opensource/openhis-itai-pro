/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.financial.OutpatientTcmDecoctionChargeItemRecognizer;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.ClearHerbalSufferingFlagCommand;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.command.SoftDeleteStoppedServiceRequestAfterDecoctionRefundCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 门诊代煎费退款终态后同步医生站方头代煎语义（AS-OP-FIN-003 · IF-REF-05 / AS-OP-DOR-010 · BR-TCM-RX-009）。
 *
 * <p>收银 {@code completeOutpatientBillRefund} 后调用：软删因申请退费而 {@code STOPPED} 的代煎 companion SR，
 * 并清方内饮片 {@code suffering_flag}，使中医 Tab 不再展示「代煎中药」且可再次补充。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientTcmDecoctionRefundDoctorStationSync {

    private final ChargeItemQueryService chargeItemQueryService;
    private final OutpatientTcmDecoctionChargeItemRecognizer outpatientTcmDecoctionChargeItemRecognizer;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final TcmDecoctionBasedOnPrescriptionResolver tcmDecoctionBasedOnPrescriptionResolver;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;

    /**
     * 代煎收费行退款完成后：收口医生站代煎勾选与停嘱 companion。
     *
     * @param encounterId            就诊主键
     * @param refundedChargeItemIds  本笔已收口为 {@code REFUNDED} 的收费行主键（可含非代煎）
     * @param operatorId             退费操作人
     */
    public void syncAfterDecoctionChargesRefunded(
        Long encounterId,
        Collection<Long> refundedChargeItemIds,
        Long operatorId
    ) {
        // Gate：缺就诊/行集/操作人时不做同步，避免误清他方方头
        if (encounterId == null || refundedChargeItemIds == null || refundedChargeItemIds.isEmpty()
            || operatorId == null) {
            return;
        }
        // Pre：装载本笔退款收费行并筛代煎 companion 锚点
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(refundedChargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Set<Long> decoctionServiceRequestIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // Gate：仅本就诊代煎行
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                continue;
            }
            if (!this.outpatientTcmDecoctionChargeItemRecognizer.isDecoctionChargeItem(chargeItem)) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(StringUtils.trimToEmpty(chargeItem.getServiceTable()))) {
                continue;
            }
            if (chargeItem.getServiceId() == null) {
                continue;
            }
            decoctionServiceRequestIds.add(chargeItem.getServiceId());
        }
        // 本笔无代煎退款行：静默返回
        if (decoctionServiceRequestIds.isEmpty()) {
            return;
        }
        // Cmd：停嘱 companion 软删，避免「有效代煎」残留挡住再补充
        this.softDeleteStoppedDecoctionCompanions(decoctionServiceRequestIds, operatorId);
        // Pre：经 based_on→药嘱反查方号，再清方头代煎勾选
        Map<Long, String> prescriptionByServiceRequestId =
            this.tcmDecoctionBasedOnPrescriptionResolver.mapServiceRequestIdsToHerbalPrescriptionNos(
                decoctionServiceRequestIds);
        Set<String> prescriptionNos = new LinkedHashSet<>();
        for (String prescriptionNo : prescriptionByServiceRequestId.values()) {
            if (StringUtils.isEmpty(prescriptionNo)) {
                continue;
            }
            prescriptionNos.add(prescriptionNo.trim());
        }
        if (prescriptionNos.isEmpty()) {
            return;
        }
        Set<Long> herbalLineIds = new LinkedHashSet<>();
        for (String prescriptionNo : prescriptionNos) {
            herbalLineIds.addAll(this.collectPositiveHerbalLineIds(encounterId, prescriptionNo));
        }
        // 无方内饮片行时仅 SR 已收口；跳过旗标写
        if (herbalLineIds.isEmpty()) {
            return;
        }
        // Cmd：回写方头代煎=否（与取消代煎 void 路径同领域写）
        ClearHerbalSufferingFlagCommand clearCmd = new ClearHerbalSufferingFlagCommand();
        clearCmd.setMedicationRequestIds(new ArrayList<>(herbalLineIds));
        clearCmd.setOperatorId(operatorId);
        this.medicationRequestDomainService.clearSufferingFlagAfterDecoctionVoided(clearCmd);
    }

    /**
     * 对已停嘱的代煎 companion 批量软删（非 STOPPED 跳过，留给 COMPLETED 等历史态）。
     *
     * @param serviceRequestIds 代煎收费锚定的 SR 主键
     * @param operatorId        操作人
     */
    private void softDeleteStoppedDecoctionCompanions(Set<Long> serviceRequestIds, Long operatorId) {
        Map<Long, ServiceRequestListModel> byId =
            this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Set<Long> stoppedIds = new LinkedHashSet<>();
        for (Long serviceRequestId : serviceRequestIds) {
            ServiceRequestListModel model = byId.get(serviceRequestId);
            if (model == null) {
                continue;
            }
            // Gate：仅申请退费「未完成 stop」路径留下的 STOPPED companion
            if (!RequestStatus.STOPPED.getCode().equals(StringUtils.trimToEmpty(model.getStatusEnum()))) {
                continue;
            }
            stoppedIds.add(serviceRequestId);
        }
        if (stoppedIds.isEmpty()) {
            return;
        }
        SoftDeleteStoppedServiceRequestAfterDecoctionRefundCommand softDeleteCmd =
            new SoftDeleteStoppedServiceRequestAfterDecoctionRefundCommand();
        softDeleteCmd.setServiceRequestIds(stoppedIds);
        softDeleteCmd.setOperatorId(operatorId);
        this.serviceRequestDomainService.softDeleteStoppedServiceRequestAfterDecoctionRefunded(softDeleteCmd);
    }

    /**
     * 收集本就诊同方正向中药饮片行主键（排除退药逆向复制行）。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药方头处方号
     * @return 饮片申请主键集合
     */
    private Set<Long> collectPositiveHerbalLineIds(Long encounterId, String prescriptionNo) {
        Set<Long> ids = new LinkedHashSet<>();
        List<MedicationRequestListQueryModel> rows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(prescriptionNo));
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // Pre：同方号可能跨就诊
            if (!encounterId.equals(row.getEncounterId())) {
                continue;
            }
            // Gate：排除退费逆向复制饮片（BR-TCM-RX-013）
            if (row.getRefundMedicineId() != null) {
                continue;
            }
            ids.add(row.getId());
        }
        return ids;
    }
}
