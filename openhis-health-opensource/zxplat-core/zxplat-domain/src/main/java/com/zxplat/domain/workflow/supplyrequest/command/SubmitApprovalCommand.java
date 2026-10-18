/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应请求提交审批命令（同 {@code bus_no} 全行进入审核中，见 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService#submitApproval}）。
 *
 * <p>状态写入使用 {@link com.zxplat.domain.share.enums.SupplyStatus} 的 {@code getCode()} 字符串（B.10.8.1）。</p>
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
        /**
         * 提交人登录科室（{@code adm_organization.id}）。
         * 非空时覆盖同号行的领用科室，作为汇总领药单申领科室。
         */
        private Long consumingOrganizationId;
    }
}
