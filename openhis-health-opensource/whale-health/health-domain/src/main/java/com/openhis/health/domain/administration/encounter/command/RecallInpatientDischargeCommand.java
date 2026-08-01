/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 出院召回命令（DM-ADM-002 §6.1.25）：将已清床离院（{@code DISCHARGED}）的住院就诊拉回已入科态。
 *
 * <p>应用层须在同事务内完成反结（AS-IP-FIN-004）后再调用；本命令只迁移就诊主档并 INSERT 活动 {@code WARD}，
 * 不新增活动床、不占物理床。</p>
 */
@Getter
@Setter
public class RecallInpatientDischargeCommand
    extends BatchCommand<RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条出院召回项。 */
    @Getter
    @Setter
    public static class RecallInpatientDischargeItemCommand {

        /** 住院就诊主键。 */
        private Long encounterId;

        /**
         * 末次已完成病区位置主键（{@code adm_location.id}，形态 {@code WARD}）。
         *
         * <p>出院召回（{@code DISCHARGED} 轨）必填，供 INSERT 活动 {@code WARD}。</p>
         */
        private Long wardLocationId;

        /** 召回生效时刻；空则由领域服务取当前时间。 */
        private LocalDateTime recalledAt;
    }
}
