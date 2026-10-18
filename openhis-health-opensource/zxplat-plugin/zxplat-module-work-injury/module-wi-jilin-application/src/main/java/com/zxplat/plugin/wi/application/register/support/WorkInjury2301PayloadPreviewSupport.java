/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.register.support;

import com.zxplat.plugin.wi.application.register.mapper.WorkInjury2301PayloadMapper;
import com.zxplat.plugin.wi.application.register.request.BuildWorkInjury2301PayloadRequest;
import com.zxplat.plugin.wi.application.register.response.BuildWorkInjury2301PayloadResponse;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.wi.domain.register.access.Build2301PayloadInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 【2301】Win input 预览（不发 Win）。
 *
 * <p>注入 domain 端口 {@link Build2301PayloadInvoker}；实现由 {@code wi-jilin-starter} 在
 * {@code zxplat.modules.work-injury.enabled=true} 时装配。</p>
 */
@Component
@RequiredArgsConstructor
public class WorkInjury2301PayloadPreviewSupport {

    private final ObjectProvider<Build2301PayloadInvoker> build2301PayloadInvoker;

    /**
     * 按就诊与表单字段组装 2301 业务体 input Map。
     *
     * @param request encounterId 必填；其余为 2301 表单字段
     * @return 待下发 Win 的 input
     */
    public BuildWorkInjury2301PayloadResponse preview(BuildWorkInjury2301PayloadRequest request) {
        Build2301PayloadInvoker invoker = build2301PayloadInvoker.getIfAvailable();
        if (invoker == null) {
            throw new BusinessException("工伤 2301 预览未装配（请启用 work-injury 模块）");
        }
        return WorkInjury2301PayloadMapper.toResponse(
                invoker.invoke(WorkInjury2301PayloadMapper.toCommand(request)));
    }
}
