/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院护士站患者信息保存命令（AS-IP-NUR-001 IF-IP-NUR-04）。
 *
 * <p>更新 {@code priority_enum}；对请求内各 {@code roleCode} 将活动参与者标为 COMPLETED 后 INSERT 新 ACTIVE 行
 *（禁止原地 UPDATE）；轨迹时刻由领域服务取服务端当前时间，前台不传。不改床位与就诊状态。分床写医护走 {@link AssignInpatientBedCommand}。</p>
 */
@Getter
@Setter
public class SaveNurseStationPatientCommand extends BatchCommand<SaveNurseStationPatientCommand.ItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条保存项。 */
    @Getter
    @Setter
    public static class ItemCommand {

        /** 住院就诊主键。 */
        private Long encounterId;

        /** 病情/优先级编码（{@code adm_encounter.priority_enum}）。 */
        private String priorityEnum;

        /** 责任医护变更集；按角色收口活动参与者后新建。 */
        private List<ParticipantLine> participants;
    }

    /** 参与者提交行。 */
    @Getter
    @Setter
    public static class ParticipantLine {

        /** 参与者角色编码。 */
        private String roleCode;

        /** 医务人员主键。 */
        private Long practitionerId;
    }
}
