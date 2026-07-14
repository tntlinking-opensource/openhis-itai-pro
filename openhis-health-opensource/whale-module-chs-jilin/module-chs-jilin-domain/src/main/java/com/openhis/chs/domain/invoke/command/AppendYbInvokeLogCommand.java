/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 写入 {@code yb_invoke_log} 命令（gateway 审计装饰器组装） */
@Data
@Accessors(chain = true)
public class AppendYbInvokeLogCommand {

    /** 契约 ID（fin_contract.id，可空） */
    private Long contractId;
    /** 契约 bus_no，gateway 路由机构连接 */
    private String contractBusNo;
    /** 交易号 infno */
    private String infno;
    /** 交易中文名 */
    private String infname;
    /** 发送方报文 ID */
    private String msgid;
    /** 就诊 ID */
    private Long encounterId;
    /** 支付草稿/支付单 ID */
    private Long paymentDraftId;
    /** 收费批次号 */
    private String chrgBchno;
    /** 预结算 ID */
    private Long preSettleId;
    /** 正式结算回执 ID */
    private Long settlementRecordId;
    /** 平台 infcode */
    private String infcode;
    /** 平台 err_msg 或异常摘要 */
    private String errMsg;
    /** 调用耗时（毫秒） */
    private Integer durationMs;
    /** 请求 JSON 全文 */
    private String requestJson;
    /** 响应 JSON 全文 */
    private String responseJson;
    /** 租户 ID */
    private Long tenantId;
}
