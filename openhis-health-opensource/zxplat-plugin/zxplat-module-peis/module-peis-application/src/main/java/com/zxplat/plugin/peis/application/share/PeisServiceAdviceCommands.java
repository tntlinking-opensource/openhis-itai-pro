/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.share;

import com.zxplat.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;

/**
 * PEIS 内联医嘱/费用锚点命令装配（D1：不依赖 HIS {@code shared.advice.ServiceAdviceCommandAssembler}）。
 */
public final class PeisServiceAdviceCommands {

    /**
     * 禁止实例化。
     */
    private PeisServiceAdviceCommands() {
    }

    /**
     * 装配「删除诊疗医嘱草稿」领域命令。
     *
     * @param requestId  诊疗申请主键
     * @param operatorId 操作人
     * @return 删除草稿命令
     */
    public static RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(
            Long requestId,
            Long operatorId) {
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
     * @return 作废草稿费用命令
     */
    public static VoidUnbilledDraftChargeItemsByServiceAnchorCommand toVoidDraftChargeAnchor(
            Long encounterId,
            String serviceTable,
            Long serviceId,
            Long operatorId) {
        VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd =
                new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(encounterId);
        cmd.setServiceTable(serviceTable);
        cmd.setServiceId(serviceId);
        return cmd;
    }
}
