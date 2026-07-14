/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.query.access;

import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.query.result.QueryBpcOrderResult;

/**
 * HTTPS 渠道查单出站端口（bridge {@code BpcQueryInvoker} 实现，P1+ 接线 gateway）。
 */
public interface BpcHttpsQueryAccess {

    /**
     * 向银联 BPC HTTPS 渠道查单。
     *
     * @param command 查单条件
     * @return 平台查单结果；未实现时返回 {@code success=false}
     */
    QueryBpcOrderResult queryRemote(QueryBpcOrderCommand command);
}
