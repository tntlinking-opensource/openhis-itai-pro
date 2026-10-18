/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新患者地址全文命令（挂号读卡回写等窄写路径，不触碰省市区拆分字段）。
 */
@Getter
@Setter
public class UpdatePatientAddressCommand extends BatchCommand<UpdatePatientAddressCommand.UpdatePatientAddressItemCommand> {

    /**
     * 更新患者地址全文子命令。
     */
    @Getter
    @Setter
    public static class UpdatePatientAddressItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 地址全文（身份证读卡户籍地址等）。
         */
        private String address;
    }
}
