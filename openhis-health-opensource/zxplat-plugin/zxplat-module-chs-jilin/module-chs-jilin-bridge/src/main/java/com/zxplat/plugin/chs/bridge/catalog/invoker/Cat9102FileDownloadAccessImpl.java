/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.invoker;

import com.zxplat.plugin.chs.domain.catalog.access.Cat9102FileDownloadAccess;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.scheme.catalog.assembler.Catalog9102InputAssembler;
import com.zxplat.plugin.chs.gateway.session.ChsSignCredentialResolver;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【9102】文件下载 bridge 出站实现（P12）。
 *
 * <p><b>职责：</b>组装 9102 入参并在 bridge 内构建签到出站上下文，经 {@link com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient} 下载 ZIP 字节流。</p>
 * <p><b>实现端口：</b>{@link com.zxplat.plugin.chs.domain.catalog.access.Cat9102FileDownloadAccess}</p>
 * <p><b>调用方：</b>{@code CatalogDownloadPhase1Workflow}、{@code Recon3202Workflow}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
@Component
@RequiredArgsConstructor
public class Cat9102FileDownloadAccessImpl implements Cat9102FileDownloadAccess {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog9102InputAssembler catalog9102InputAssembler;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    @Override
    public byte[] download(String contractBusNo, String fileQuryNo, String filename, String fixmedinsCode) {
        var input9102 = catalog9102InputAssembler.assemble(fileQuryNo, filename, fixmedinsCode);
        return gatewayClient.downloadFile(contractBusNo, input9102, buildInvokeContext());
    }

    private InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再操作医保文件下载");
        InvokeContext partial = InvokeContext.builder().operatorId(String.valueOf(operatorId)).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }
}
