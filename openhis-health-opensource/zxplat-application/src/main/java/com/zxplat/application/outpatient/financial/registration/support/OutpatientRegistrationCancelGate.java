/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.share.constants.OutpatientRegistrationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊退号收费前置门禁（IF-REG-09 · BR-REG-015）。
 *
 * <p>排除挂号费锚点（{@link TableNameConstants#ADM_HEALTHCARE_SERVICE}）。
 * 退药/退耗已在门诊退费执行前（IF-REF-04）收口，本门禁<strong>不再</strong>读取发放事实；
 * 诊疗项目执行记录亦不在本门禁范围。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientRegistrationCancelGate {

    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 判定就诊是否存在阻断退号的非挂号锚点收费事实。
     *
     * <p>仅校验待收费 / 已收费 / 退费中；药耗发放态由退费链 IF-REF-04 保证，费用全退后视为已收口。</p>
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
        return this.evaluateRefundingChargeBlock(chargeItems);
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
            // 仍有待收费行：须医生撤回已签发医嘱后再退号
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
            // 仍有已收费未退行：须先完成门诊退费后再退号
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
            // 退费处理中：须待退费完成后再退号
            if (ChargeItemStatus.REFUNDING.getCode().equals(chargeItem.getStatusEnum())) {
                return OutpatientRegistrationCancelBlockResult.blocked(
                    OutpatientRegistrationConstants.CANCEL_REASON_CODE_REFUNDING_CHARGE,
                    OutpatientRegistrationConstants.CANCEL_MESSAGE_REFUNDING_CHARGE);
            }
        }
        return OutpatientRegistrationCancelBlockResult.notBlocked();
    }
}
