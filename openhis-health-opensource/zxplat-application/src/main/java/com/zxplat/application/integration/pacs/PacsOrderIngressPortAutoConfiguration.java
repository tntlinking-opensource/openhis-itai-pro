/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.integration.pacs;

import com.zxplat.plugin.pacs.domain.port.PacsOrderIngressPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 未装 pacs starter 时注册 {@link NoOpPacsOrderIngressPort}。
 *
 * <p>{@code @ConditionalOnMissingBean} 只能用在 AutoConfiguration，不能写在被
 * {@code scanBasePackages} 扫到的 {@code @Component} 上（条件会误判并摘掉候选 Bean）。</p>
 */
@AutoConfiguration(afterName = "com.zxplat.plugin.pacs.application.config.PacsAutoConfiguration")
public class PacsOrderIngressPortAutoConfiguration {

    /**
     * @return 空实现；classpath 已有 {@link PacsOrderIngressPort}（真实现）时不注册
     */
    @Bean
    @ConditionalOnMissingBean(PacsOrderIngressPort.class)
    PacsOrderIngressPort noOpPacsOrderIngressPort() {
        return new NoOpPacsOrderIngressPort();
    }
}
