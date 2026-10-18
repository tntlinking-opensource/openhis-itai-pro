/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 开立转科申请命令。
 */
@Getter
@Setter
public class CreateTransferDepartmentRequestCommand extends BatchCommand<CreateTransferDepartmentRequestCommand.CreateTransferDepartmentRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建调拨科室请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateTransferDepartmentRequestItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键（与就诊一致，供列表与读模型投影）。 */
        private Long patientId;
        /** 目标机构主键。 */
        private Long targetOrgId;
        /** 原因文本。 */
        private String reasonText;
    }
}
