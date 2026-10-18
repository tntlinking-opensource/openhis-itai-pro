/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.invoker;

import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.CatFileDownloadInput;
import com.zxplat.plugin.chs.gateway.scheme.catalog.assembler.CatalogFileDownloadInputAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【13xx】文件型目录单轮下载出站（1301/1302/1305/1306…）。
 * <p>调用 {@code gatewayClient.invoke} 获取文件查询号与元数据，不负责 9102 ZIP 下载。</p>
 */
@Component
@RequiredArgsConstructor
public class Cat13xxInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final CatalogFileDownloadInputAssembler catalogFileDownloadInputAssembler;

    /**
     * @param contractBusNo 契约 bus_no
     * @param infno         接口号（如 1301）
     * @param requestVer    本轮 {@code data.ver}
     * @param invokeContext 签到/操作员上下文
     * @return 平台原始应答
     */
    public ResultBody invoke(
            String contractBusNo,
            String infno,
            String requestVer,
            InvokeContext invokeContext) {
        CatFileDownloadInput input = catalogFileDownloadInputAssembler.assemble(requestVer);
        return gatewayClient.invoke(contractBusNo, infno, input, invokeContext);
    }
}
