/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废已同意期初单状态命令（仅供应聚合：置 {@code EXPIRED_INVALIDATED}）。
 *
 * <p>库存回滚与追溯 T1 冲销由应用层 {@code InventoryOpeningVoidOrchestrator} 同事务编排
 * （DOC-CONFLICT：AS 写 Domain 内聚门面；工程 B.6 禁跨聚合，故拆为领域状态迁移 + 应用编排门面）。</p>
 */
@Getter
@Setter
public class VoidOpeningCommand extends BatchCommand<VoidOpeningCommand.VoidOpeningItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 作废项（不支持批量时仍保留双形态外壳）。
     */
    @Getter
    @Setter
    public static class VoidOpeningItemCommand {
        /** 待作废期初单业务单号（须当前 AGREE）。 */
        private String busNo;
        /** 作废原因（可选，写入行备注/原因字段由调用方决定是否落库）。 */
        private String reason;
    }
}
