/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 住院互换床命令（DM-ADM-002 §6.1.18a / AS-IP-NUR-002 IF-IP-NUR-ATD-02）。
 *
 * <p>两名 {@code IMP + IN_PROGRESS} 就诊对调当前活动床位级 {@code adm_encounter_location}；
 * 轨迹时刻由领域服务取服务端当前时间。领域内发布两条
 * {@link com.openhis.health.domain.share.events.administration.encounter.BedTransferredEvent}。
 * 床位 {@code operational_enum}（{@code IN_USE}/{@code NOT_IN_USE}）由应用层编排 Location {@code releaseBed}/{@code occupyBed}。</p>
 */
@Getter
@Setter
public class SwapInpatientBedsCommand extends BatchCommand<SwapInpatientBedsCommand.SwapInpatientBedsItemCommand> {

    /** 操作人主键（审计与事件载荷，可选）。 */
    private Long operatorId;

    /**
     * 单条互换项；护士站拖放互换通常一次请求仅一条。
     */
    @Getter
    @Setter
    public static class SwapInpatientBedsItemCommand {

        /** 就诊 A 主键（发起拖放方或列表选中患者之一）。 */
        private Long encounterIdA;

        /** 就诊 B 主键（目标占床患者，须与 A 不同且双方均已分床）。 */
        private Long encounterIdB;

        /** A 迁入的病房 Location（原 B 侧病房）。 */
        private Long toHouseIdA;

        /** B 迁入的病房 Location（原 A 侧病房）。 */
        private Long toHouseIdB;
    }
}
