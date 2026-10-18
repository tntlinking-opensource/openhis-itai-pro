/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.system.service;

import com.zxplat.plugin.pacs.application.config.system.response.GetSystemParamsResponse;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshot;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PACS 系统参数（AS-PACS-CFG-004 · 只读）。
 *
 * <p>接入：{@code POST /api/app/pacsSystemConfig/{methodName}}。</p>
 * <p>仅依赖 {@link PacsAdapterRuntimeSnapshotPort}，不直引 adapter Properties。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsSystemConfigAppService {

    private final PacsAdapterRuntimeSnapshotPort pacsAdapterRuntimeSnapshotPort;

    /**
     * IF-PACS-CFG-04：读取当前运行参数。
     *
     * @return 参数快照
     */
    public GetSystemParamsResponse getSystemParams() {
        // Gate：只读；无操作人门禁
        GetSystemParamsResponse resp = new GetSystemParamsResponse();
        // Pre：经 Port 读取 adapter 运行快照（未装配时为 NoOp 默认值）
        PacsAdapterRuntimeSnapshot snapshot = pacsAdapterRuntimeSnapshotPort.getSnapshot();
        // Asm：映射出参；remark 区分是否已装配真实 adapter
        resp.setAdapterEnabled(snapshot.enabled());
        resp.setMockOutboundSuccess(snapshot.mockOutboundSuccess());
        if (snapshot.enabled()) {
            resp.setRemark("来源 zxplat.modules.pacs.adapter.*（经 PacsAdapterRuntimeSnapshotPort）");
        } else {
            resp.setRemark("adapter 未装配；默认值来自文档口径（NoOp Port）");
        }
        return resp;
    }
}
