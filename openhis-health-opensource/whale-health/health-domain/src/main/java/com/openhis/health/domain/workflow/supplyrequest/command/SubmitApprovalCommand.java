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

package com.openhis.health.domain.workflow.supplyrequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应请求提交审批命令（同 {@code bus_no} 全行进入审核中，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#submitApproval}）。
 *
 * <p>状态写入使用 {@link com.openhis.health.domain.share.enums.SupplyStatus} 的 {@code getCode()} 字符串（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SubmitApprovalCommand extends BatchCommand<SubmitApprovalCommand.SubmitApprovalItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单张单据提交参数。
     */
    @Getter
    @Setter
    public static class SubmitApprovalItemCommand {
        /** 业务单号。 */
        private String busNo;
        /**
         * 申请人主键：须由应用层从当前登录会话解析的医务人员主键写入，禁止信任前端代传；
         * 提交审批时与 {@code apply_time=当前时间} 一并落库到同号全部明细行。
         */
        private Long applicantId;
    }
}
