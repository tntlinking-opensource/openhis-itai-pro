/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.gateway.client;

import com.openhis.elep.gateway.scheme.share.model.ElepGatewayResponse;
import com.openhis.elep.gateway.client.remote.ElepInvokeContext;
import com.openhis.elep.domain.gateway.ElepOpCode;

/**
 * 电子处方中台网关客户端。
 * <p>业务侧只依赖此接口；embedded 由 application 提供 LocalImpl。</p>
 */
public interface ElepGatewayClient {

    /**
     * 调用电子处方中台 A000x 接口。
     *
     * @param contractBusNo 契约业务编码（fin_contract.bus_no）
     * @param opCode        操作码
     * @param input         业务入参
     * @param context       可选上下文，可为 null
     */
    ElepGatewayResponse invoke(
            String contractBusNo, ElepOpCode opCode, Object input, ElepInvokeContext context);
}
