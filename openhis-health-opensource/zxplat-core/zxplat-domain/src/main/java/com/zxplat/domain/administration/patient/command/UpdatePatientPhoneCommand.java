/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.command.BatchCommand;


/**
 * 更新患者手机号命令。
 */
@Getter
@Setter
public class UpdatePatientPhoneCommand extends BatchCommand<UpdatePatientPhoneCommand.UpdatePatientPhoneItemCommand> {

    /**
     * 更新患者手机号子命令。
     */
    @Getter
    @Setter
    public static class UpdatePatientPhoneItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 新手机号。
         */
        private String phone;
    }
}
