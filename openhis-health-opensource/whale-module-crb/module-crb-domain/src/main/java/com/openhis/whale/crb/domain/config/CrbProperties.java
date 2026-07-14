/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.config;

import lombok.Getter;
import lombok.Setter;

/**
 * CRB 模块运行时配置（由 application 层 {@code @ConfigurationProperties} 绑定后写入）。
 */
@Getter
@Setter
public class CrbProperties {

    private FrontSoftware frontSoftware = new FrontSoftware();
    private Push push = new Push();
    private Id id = new Id();

    /** 前置软件连接参数。 */
    @Getter
    @Setter
    public static class FrontSoftware {
        private String baseUrl = "";
        private String license = "";
        private String orgCode = "";
    }

    /** 推送 Worker 与 Mock 开关。 */
    @Getter
    @Setter
    public static class Push {
        private boolean mockEnabled = true;
        private int realtimeMaxDelaySeconds = 180;
        private int maxRetry = 5;
    }

    /** EMR id 生成策略（Mapper 阶段使用）。 */
    @Getter
    @Setter
    public static class Id {
        private String patientPattern = "{orgCode}_{patientBusNo}";
        private boolean activityVersioned = false;
    }
}
