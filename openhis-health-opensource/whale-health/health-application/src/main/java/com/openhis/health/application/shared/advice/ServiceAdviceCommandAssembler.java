/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;

/**
 * 医嘱诊疗申请草稿/签退命令装配（门诊/住院 Mapper 与 orchestration 共用）。
 */
public final class ServiceAdviceCommandAssembler {

    /** 工具类禁止实例化。 */
    private ServiceAdviceCommandAssembler() {
    }

    /**
     * 装配「删除诊疗医嘱草稿」领域命令。
     *
     * @param requestId  诊疗申请主键
     * @param operatorId 操作人
     * @return {@link RemoveServiceRequestDraftCommand}
     */
    public static RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(
        Long requestId,
        Long operatorId
    ) {
        RemoveServiceRequestDraftCommand cmd = new RemoveServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item =
            new RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand();
        item.setId(requestId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「按医嘱锚点作废未计费草稿费用」命令。
     *
     * @param encounterId  就诊主键
     * @param serviceTable 医嘱申请物理表名
     * @param serviceId    医嘱申请主键
     * @param operatorId   操作人
     * @return {@link VoidUnbilledDraftChargeItemsByServiceAnchorCommand}
     */
    public static VoidUnbilledDraftChargeItemsByServiceAnchorCommand toVoidDraftChargeAnchor(
        Long encounterId,
        String serviceTable,
        Long serviceId,
        Long operatorId
    ) {
        VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd = new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(encounterId);
        cmd.setServiceTable(serviceTable);
        cmd.setServiceId(serviceId);
        return cmd;
    }

    /**
     * 装配已签发诊疗申请的签退命令。
     *
     * @param id         诊疗申请主键
     * @param operatorId 操作人
     * @return {@link WithdrawSignedServiceRequestCommand}
     */
    public static WithdrawSignedServiceRequestCommand toWithdrawServiceRequestCommand(Long id, Long operatorId) {
        WithdrawSignedServiceRequestCommand cmd = new WithdrawSignedServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand item =
            new WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand();
        item.setId(id);
        cmd.setItem(item);
        return cmd;
    }
}
