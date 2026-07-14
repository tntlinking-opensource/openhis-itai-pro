/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.OutpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊退号临床/收费前置门禁（IF-REG-09 · BR-REG-015）。
 *
 * <p>排除挂号费锚点（{@link TableNameConstants#ADM_HEALTHCARE_SERVICE}）；诊疗项目执行记录不在本门禁范围。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientRegistrationCancelGate {

    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 判定就诊是否存在阻断退号的收费/发药/耗材执行事实。
     *
     * @param encounterId 门诊就诊主键
     * @return 阻断结果；未命中阻断规则时 {@link OutpatientRegistrationCancelBlockResult#isBlocked()} 为 false
     */
    public OutpatientRegistrationCancelBlockResult evaluateClinicalBillingBlock(Long encounterId) {
        if (encounterId == null) {
            return OutpatientRegistrationCancelBlockResult.notBlocked();
        }
        // Pre：非挂号锚点收费明细（叠加 BR-REF-020 排除已再生替代的源 SETTLED 行）
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setEncounterId(encounterId);
        chargeQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        chargeQuery.setExcludeSupersededSettledLines(Boolean.TRUE);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(chargeQuery);

        // Gate：待收费须医生撤回签发内容（PLANNED）
        OutpatientRegistrationCancelBlockResult pendingChargeBlock = this.evaluatePendingChargeBlock(chargeItems);
        if (pendingChargeBlock.isBlocked()) {
            return pendingChargeBlock;
        }
        // Gate：已收费须门诊账单全退（SETTLED）
        OutpatientRegistrationCancelBlockResult settledChargeBlock = this.evaluateSettledChargeBlock(chargeItems);
        if (settledChargeBlock.isBlocked()) {
            return settledChargeBlock;
        }
        // Gate：退费处理中须等待收口（REFUNDING）
        OutpatientRegistrationCancelBlockResult refundingChargeBlock = this.evaluateRefundingChargeBlock(chargeItems);
        if (refundingChargeBlock.isBlocked()) {
            return refundingChargeBlock;
        }
        // Gate：正向药品发放：药房在途或已发未全退须分别收口
        OutpatientRegistrationCancelBlockResult medicationBlock = this.evaluateMedicationDispenseBlock(encounterId);
        if (medicationBlock.isBlocked()) {
            return medicationBlock;
        }
        // Gate：耗材发放须取消执行（回退至待配药）
        return this.evaluateDeviceExecutionCancelBlock(encounterId);
    }

    /**
     * Gate：是否存在非挂号锚点的待收费明细。
     *
     * @param chargeItems 就诊收费明细
     * @return 阻断结果
     */
    private OutpatientRegistrationCancelBlockResult evaluatePendingChargeBlock(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return OutpatientRegistrationCancelBlockResult.notBlocked();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (ChargeItemStatus.PLANNED.getCode().equals(chargeItem.getStatusEnum())) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_PENDING_CHARGE,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_PENDING_CHARGE);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }

    /**
     * Gate：是否存在非挂号锚点的已结算明细。
     *
     * @param chargeItems 就诊收费明细
     * @return 阻断结果
     */
    private OutpatientRegistrationCancelBlockResult evaluateSettledChargeBlock(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return OutpatientRegistrationCancelBlockResult.notBlocked();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_SETTLED_CHARGE,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_SETTLED_CHARGE);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }

    /**
     * Gate：是否存在非挂号锚点的退费处理中明细。
     *
     * @param chargeItems 就诊收费明细
     * @return 阻断结果
     */
    private OutpatientRegistrationCancelBlockResult evaluateRefundingChargeBlock(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return OutpatientRegistrationCancelBlockResult.notBlocked();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (ChargeItemStatus.REFUNDING.getCode().equals(chargeItem.getStatusEnum())) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_REFUNDING_CHARGE,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_REFUNDING_CHARGE);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }

    /**
     * Gate：正向门诊/临时药品发放是否未收口（药房在途或已发未全退）。
     *
     * @param encounterId 就诊主键
     * @return 阻断结果
     */
    private OutpatientRegistrationCancelBlockResult evaluateMedicationDispenseBlock(Long encounterId) {
        List<MedicationDispense> dispenseRows =
            medicationDispenseQueryService.listUndeletedMedicationDispensesByEncounterId(encounterId);
        if (dispenseRows.isEmpty()) {
            return OutpatientRegistrationCancelBlockResult.notBlocked();
        }
        Set<Long> medReqIdsWithRefundedReturn = new HashSet<>();
        for (MedicationDispense row : dispenseRows) {
            if (!this.isRefundTypeDispense(row)) {
                continue;
            }
            if (DispenseStatus.REFUNDED.equals(row.getStatusEnum()) && row.getMedReqId() != null) {
                medReqIdsWithRefundedReturn.add(row.getMedReqId());
            }
        }
        for (MedicationDispense row : dispenseRows) {
            if (!this.isForwardOutpatientMedicationDispense(row)) {
                continue;
            }
            DispenseStatus status = row.getStatusEnum();
            if (status == null) {
                continue;
            }
            // 药房在途（待配药/已配药/待发药/已汇总待核发）：须先完成门诊退费再退号
            if (this.isPharmacyInTransitMedicationStatus(status)) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_MEDICATION_IN_PHARMACY,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_MEDICATION_IN_PHARMACY);
            }
            // 已发完且同医嘱存在已确认退药对冲行：视为药全退
            if (DispenseStatus.COMPLETED.equals(status) || DispenseStatus.PART_COMPLETED.equals(status)) {
                if (row.getMedReqId() != null && medReqIdsWithRefundedReturn.contains(row.getMedReqId())) {
                    continue;
                }
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_MEDICATION_NOT_RETURNED,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_MEDICATION_NOT_RETURNED);
            }
            // 待退或部分退：退药流程未收口
            if (this.isBlockingMedicationReturnStatus(status)) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_MEDICATION_NOT_RETURNED,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_MEDICATION_NOT_RETURNED);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }

    /**
     * Gate：耗材发放是否仍处于护士已执行态（未取消执行）。
     *
     * @param encounterId 就诊主键
     * @return 阻断结果
     */
    private OutpatientRegistrationCancelBlockResult evaluateDeviceExecutionCancelBlock(Long encounterId) {
        List<DeviceDispenseListModel> dispenseRows =
            deviceDispenseQueryService.listActiveDeviceDispensesByEncounterId(encounterId);
        for (DeviceDispenseListModel row : dispenseRows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            if (this.isBlockingExecutedDeviceStatus(row.getStatusEnum())) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_DEVICE_NOT_CANCELLED,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_DEVICE_NOT_CANCELLED);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }

    /**
     * 是否门诊/临时正向发放行（退药 REFUND 类型不参与本门禁）。
     *
     * @param row 发放事实
     * @return 正向门诊发放时为 true
     */
    private boolean isForwardOutpatientMedicationDispense(MedicationDispense row) {
        if (row == null) {
            return false;
        }
        MedicationDispenseType type = row.getDispenseEnum();
        if (type == null) {
            return false;
        }
        return MedicationDispenseType.OUTPATIENT.equals(type) || MedicationDispenseType.TEMPORARY.equals(type);
    }

    /**
     * 是否退药类型发放行。
     *
     * @param row 发放事实
     * @return 退药类型时为 true
     */
    private boolean isRefundTypeDispense(MedicationDispense row) {
        if (row == null) {
            return false;
        }
        return MedicationDispenseType.REFUND.equals(row.getDispenseEnum());
    }

    /**
     * 正向药品是否处于药房在途（待配药/已配药/待发药），尚未发药给患者。
     *
     * @param status 发放状态
     * @return 阻断退号且提示先完成门诊退费时为 true
     */
    private boolean isPharmacyInTransitMedicationStatus(DispenseStatus status) {
        return DispenseStatus.PREPARATION.equals(status)
            || DispenseStatus.PREPARED.equals(status)
            || DispenseStatus.IN_PROGRESS.equals(status)
            || DispenseStatus.SUMMARIZED.equals(status);
    }

    /**
     * 正向药品是否处于退药流程未收口态。
     *
     * @param status 发放状态
     * @return 阻断退号且提示完成退药时为 true
     */
    private boolean isBlockingMedicationReturnStatus(DispenseStatus status) {
        return DispenseStatus.PENDING_REFUND.equals(status)
            || DispenseStatus.PART_REFUND.equals(status);
    }

    /**
     * 耗材发放是否处于护士已执行且未对称回退的状态。
     *
     * @param statusCode 发放状态持久化码
     * @return 阻断退号时为 true
     */
    private boolean isBlockingExecutedDeviceStatus(String statusCode) {
        return DispenseStatus.PREPARED.getCode().equals(statusCode)
            || DispenseStatus.IN_PROGRESS.getCode().equals(statusCode)
            || DispenseStatus.COMPLETED.getCode().equals(statusCode)
            || DispenseStatus.PART_COMPLETED.getCode().equals(statusCode);
    }
}
