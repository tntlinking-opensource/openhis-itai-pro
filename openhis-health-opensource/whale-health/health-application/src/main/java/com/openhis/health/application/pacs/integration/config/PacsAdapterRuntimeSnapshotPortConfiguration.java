/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.integration.config;

import com.openhis.health.application.pacs.integration.port.NoOpPacsAdapterRuntimeSnapshotPort;
import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PACS adapter 运行快照端口缺省装配：无 adapter 时注入 NoOp。
 */
@Configuration
public class PacsAdapterRuntimeSnapshotPortConfiguration {

    /**
     * adapter 未装配时的占位快照（CFG-04 默认值）。
     *
     * @return NoOp 端口
     */
    @Bean
    @ConditionalOnMissingBean(PacsAdapterRuntimeSnapshotPort.class)
    PacsAdapterRuntimeSnapshotPort noOpPacsAdapterRuntimeSnapshotPort() {
        return new NoOpPacsAdapterRuntimeSnapshotPort();
    }
}
