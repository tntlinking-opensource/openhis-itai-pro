/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 确认转床命令（DM-ADM-002 §6.1.18 / AS-IP-NUR-002 换空床）。
 *
 * <p>已入科患者从 {@code fromBedId} 迁至 {@code toBedId}，保持 {@code IN_PROGRESS}；
 * 不维护参与者；轨迹时刻由领域服务取服务端当前时间。应用层须对原床 {@code releaseBed}、新床 {@code occupyBed}。</p>
 */
@Getter
@Setter
public class ConfirmBedTransferCommand extends BatchCommand<ConfirmBedTransferCommand.ConfirmBedTransferItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条转床确认项。
     */
    @Getter
    @Setter
    public static class ConfirmBedTransferItemCommand {

        /** 住院就诊主键。 */
        private Long encounterId;

        /** 当前活动床位 Location 主键（须与库内活动床一致，防并发占床）。 */
        private Long fromBedId;

        /** 目标病房 Location 主键（{@code form_enum=HOUSE}）。 */
        private Long toHouseId;

        /** 目标空床 Location 主键（{@code form_enum=BED}）。 */
        private Long toBedId;

        /** 新位置形态 code；空则领域默认 {@code BED}。 */
        private String toFormEnum;

        /** 目标床位编码（事件/展示，可选）。 */
        private String toBedCode;
    }
}
