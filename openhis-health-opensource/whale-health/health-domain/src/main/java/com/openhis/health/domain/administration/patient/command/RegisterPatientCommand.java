/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 新建患者档案命令。
 */
@Getter
@Setter
public class RegisterPatientCommand extends BatchCommand<RegisterPatientCommand.RegisterPatientItemCommand> {

    /**
     * 操作时间。
     */
    private LocalDateTime occurredAt;

    /**
     * 新建患者档案子命令。
     */
    @Getter
    @Setter
    public static class RegisterPatientItemCommand {

        /**
         * 患者姓名。
         */
        private String name;

        /**
         * 性别编码。
         */
        private String genderEnum;

        /**
         * 出生日期。
         */
        private LocalDate birthDate;

        /**
         * 身份证号。
         */
        private String idCard;

        /**
         * 联系电话。
         */
        private String phone;

        /**
         * 临时建档标记。
         */
        private Boolean tempFlag;

        /**
         * 建档机构标识。
         */
        private Long organizationId;

        /**
         * 非身份证证件集合。
         */
        private List<PatientIdentifierItemCommand> identifierItems;
    }
}
