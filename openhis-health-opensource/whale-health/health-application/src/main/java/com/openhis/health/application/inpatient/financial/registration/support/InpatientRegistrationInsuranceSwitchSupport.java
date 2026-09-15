/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.support;

import com.openhis.health.domain.administration.chargeitem.command.SwitchChargeItemAccountCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院自费转医保：未结算费用批量切换记账账户（单 Feature 写编排）。
 */
@Component
@RequiredArgsConstructor
public class InpatientRegistrationInsuranceSwitchSupport {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDomainService chargeItemDomainService;

    /**
     * 将就诊下可切换费用行批量挂至医保账户。
     *
     * @param encounterId         住院就诊主键
     * @param insuranceAccountId  医保挂账账户主键
     * @param operatorId          操作人主键
     * @return 实际写库切换的行数
     */
    public int switchUnsettledChargeItemsToInsuranceAccount(
        Long encounterId,
        Long insuranceAccountId,
        Long operatorId) {
        if (encounterId == null || insuranceAccountId == null || operatorId == null) {
            return 0;
        }
        // Pre：加载就诊下草稿/待收费/待结算行，排除挂号锚点。
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setEncounterId(encounterId);
        queryCommand.setStatusEnumIn(this.inpatientSwitchableStatusCodes());
        queryCommand.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        queryCommand.setExcludeSupersededSettledLines(true);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        List<ChargeItem> switchableRows = this.filterInpatientSwitchableChargeItems(rows);
        List<ChargeItem> rowsToSwitch = this.collectRowsNeedingTargetAccount(switchableRows, insuranceAccountId);
        if (rowsToSwitch.isEmpty()) {
            return 0;
        }
        // Cmd：批量更新 account_id，使后续划价/结算走医保户。
        SwitchChargeItemAccountCommand command = new SwitchChargeItemAccountCommand();
        command.setOperatorId(operatorId);
        List<SwitchChargeItemAccountCommand.Item> items = new ArrayList<>();
        for (ChargeItem row : rowsToSwitch) {
            SwitchChargeItemAccountCommand.Item item = new SwitchChargeItemAccountCommand.Item();
            item.setChargeItemId(row.getId());
            item.setAccountId(insuranceAccountId);
            items.add(item);
        }
        command.setItems(items);
        chargeItemDomainService.switchChargeItemAccount(command);
        return rowsToSwitch.size();
    }

    /**
     * 住院转医保可切换的收费状态集合。
     *
     * @return 草稿/待收费/待结算状态码
     */
    private List<String> inpatientSwitchableStatusCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(ChargeItemStatus.DRAFT.getCode());
        codes.add(ChargeItemStatus.PLANNED.getCode());
        codes.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        return codes;
    }

    /**
     * 排除已结算/已退款终态行，保留可改账户的在院费用。
     *
     * @param chargeItems 就诊收费行
     * @return 可切换列表
     */
    private List<ChargeItem> filterInpatientSwitchableChargeItems(List<ChargeItem> chargeItems) {
        List<ChargeItem> result = new ArrayList<>();
        if (chargeItems == null) {
            return result;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            // 已结算不可再切账户，须先反结或走人工处理。
            if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                continue;
            }
            // 已退款终态不可再切账户。
            if (ChargeItemStatus.REFUNDED.getCode().equals(chargeItem.getStatusEnum())) {
                continue;
            }
            result.add(chargeItem);
        }
        return result;
    }

    /**
     * 收集 accountId 与目标医保户不一致的行（已归属目标户则幂等跳过）。
     *
     * @param chargeItems       可切换收费行
     * @param targetAccountId   医保账户主键
     * @return 待写库行
     */
    private List<ChargeItem> collectRowsNeedingTargetAccount(
        List<ChargeItem> chargeItems,
        Long targetAccountId) {
        List<ChargeItem> result = new ArrayList<>();
        if (chargeItems == null) {
            return result;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (targetAccountId.equals(chargeItem.getAccountId())) {
                continue;
            }
            result.add(chargeItem);
        }
        return result;
    }
}
