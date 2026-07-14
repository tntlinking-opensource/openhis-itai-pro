/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 停用公费学生资质命令。
 */
@Getter
@Setter
public class DisablePatientStudentRecordCommand extends BatchCommand<DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand> {

    /**
     * 停用学生资质子命令。
     */
    @Getter
    @Setter
    public static class DisablePatientStudentRecordItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 学生记录标识。
         */
        private Long studentId;

        /**
         * 停用原因。
         */
        private String reason;
    }
}
