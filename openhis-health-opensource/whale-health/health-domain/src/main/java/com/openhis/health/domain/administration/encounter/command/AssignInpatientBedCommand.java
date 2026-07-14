/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院分床命令（DM-ADM-002 §6.1.17 / AS-IP-NUR-002 IF-IP-NUR-ATD-02 首次分床）。
 *
 * <p>将 {@code IMP + REGISTERED} 就诊迁入目标床位并迁移为 {@code IN_PROGRESS}；
 * 同事务可写入 {@code priority_enum} 与 {@code adm_encounter_participant} 活动行。
 * 入科/轨迹 {@code start_time}/{@code end_time} 由领域服务取服务端当前时刻，前台不传。
 * 床位资源运营态须由应用层在成功后调用 Location {@code occupyBed}。</p>
 */
@Getter
@Setter
public class AssignInpatientBedCommand extends BatchCommand<AssignInpatientBedCommand.AssignInpatientBedItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条分床业务项。
     */
    @Getter
    @Setter
    public static class AssignInpatientBedItemCommand {

        /** 住院就诊主键。 */
        private Long encounterId;

        /** 目标病房主键（{@code form_enum=HOUSE}）。 */
        private Long houseId;

        /** 目标床位主键（{@code form_enum=BED}）。 */
        private Long bedId;

        /** 床位形态 code；空则默认 {@code BED}（一般不必传）。 */
        private String formEnum;

        /** 床位编码（事件与 UI 展示对照，可选）。 */
        private String bedCode;

        /** 病情优先级 code，写入 {@code adm_encounter.priority_enum}（可选）。 */
        private String priorityEnum;

        /** 责任医护（init 下拉选中）；写入时收口全部活动参与者后新建活动行（可选，建议必填）。 */
        private List<ParticipantLine> participants;
    }

    /**
     * 分床提交的责任医护行；字段语义与 {@link SaveNurseStationPatientCommand.ParticipantLine} 一致。
     */
    @Getter
    @Setter
    public static class ParticipantLine {

        /** 参与者角色 code（如住院医师、责任护士）。 */
        private String roleCode;

        /** 医务人员 Practitioner 主键。 */
        private Long practitionerId;
    }
}
