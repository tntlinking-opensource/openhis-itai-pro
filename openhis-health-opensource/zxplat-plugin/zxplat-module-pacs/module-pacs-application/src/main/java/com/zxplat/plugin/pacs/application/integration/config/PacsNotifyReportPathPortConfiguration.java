/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration.config;

import com.zxplat.plugin.pacs.application.integration.port.NoOpPacsNotifyReportPathPort;
import com.zxplat.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 报告路径回传端口缺省装配：无 adapter 时注入 NoOp。
 */
@Configuration
public class PacsNotifyReportPathPortConfiguration {

    /**
     * adapter 未装配时的占位端口（AppService 走本地日志）。
     *
     * @return NoOp 端口
     */
    @Bean
    @ConditionalOnMissingBean(PacsNotifyReportPathPort.class)
    PacsNotifyReportPathPort noOpPacsNotifyReportPathPort() {
        return new NoOpPacsNotifyReportPathPort();
    }
}
