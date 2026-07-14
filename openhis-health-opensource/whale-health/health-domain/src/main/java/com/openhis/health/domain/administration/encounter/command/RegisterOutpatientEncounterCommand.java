/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开立门诊就诊命令：创建门诊就诊事实并绑定患者、号源类型、机构与医师等。
 */
@Getter
@Setter
public class RegisterOutpatientEncounterCommand extends BatchCommand<RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 操作发生时间（挂号/开立时刻）。 */
    private LocalDateTime occurredAt;

    /** 单条门诊开立项。 */
    @Getter
    @Setter
    public static class RegisterOutpatientEncounterItemCommand {
        /** 患者主键。 */
        private Long patientId;
        /** 号源或服务类型主键。 */
        private Long serviceTypeId;
        /** 机构主键。 */
        private Long organizationId;
        /** 接诊医师主键。 */
        private Long practitionerId;
        /**
         * 接诊医师姓名展示快照（挂号环节已选医生时由应用层装配）；未选医生时为 {@code null}。
         */
        private String practitionerName;
        /** 就诊类别枚举编码。 */
        private String classEnum;

        /** 患者院内业务号快照（读模型投影）。 */
        private String patientBusNo;
        /** 患者姓名快照。 */
        private String patientName;
        /** 患者姓名拼音首字母检索码快照。 */
        private String pyStr;
        /** 患者姓名五笔首字母检索码快照。 */
        private String wbStr;
        /** 患者性别编码快照。 */
        private String genderEnum;
        /** 患者性别展示文案快照。 */
        private String genderEnumText;
        /** 联系电话快照。 */
        private String phone;
        /** 出生日期快照。 */
        private LocalDate birthDate;
        /** 年龄展示文本（如「35岁」）。 */
        private String patientAgeText;
        /** 身份证号快照（门诊信息读模型等投影用）。 */
        private String idCard;
        /** 挂号科室名称快照。 */
        private String organizationName;
        /** 号源类型名称快照。 */
        private String serviceTypeName;
        /** 挂号开始/排序时间。 */
        private LocalDateTime startTime;
        /** 展示排序。 */
        private Integer displayOrder;
        /** 挂号员主键。 */
        private Long registrarId;
        /** 挂号员姓名。 */
        private String registrarName;
    }
}
