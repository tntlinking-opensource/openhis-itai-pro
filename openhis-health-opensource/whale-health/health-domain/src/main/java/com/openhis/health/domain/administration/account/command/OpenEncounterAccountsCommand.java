/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.account.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量开立就诊账户命令：为多条就诊上下文创建账户（如门诊/住院分账），由操作人触发。
 */
@Getter
@Setter
public class OpenEncounterAccountsCommand extends BatchCommand<OpenEncounterAccountsCommand.Item> {
    /** 操作人主键（审计与权限）。 */
    private Long operatorId;

    /**
     * 单条开立项：患者、就诊、账户类型与是否默认账户。
     */
    @Getter
    @Setter
    public static class Item {
        /** 患者主键。 */
        private Long patientId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 账户类型编码（与术语或常量约定一致）。 */
        private String typeCode;
        /** 是否作为该就诊下该类型的默认账户。 */
        private Boolean defaultFlag;
        /** 费用性质业务编码（写入账户 {@code contract_no}）。 */
        private String contractNo;
    }
}
