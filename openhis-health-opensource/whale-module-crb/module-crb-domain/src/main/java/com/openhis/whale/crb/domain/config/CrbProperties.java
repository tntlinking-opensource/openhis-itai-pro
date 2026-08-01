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
    private Lab lab = new Lab();

    /** 前置软件连接参数。 */
    @Getter
    @Setter
    public static class FrontSoftware {
        private String baseUrl = "";
        private String license = "";
        /** 国家平台机构代码（写入 receive 的 orgCode）。 */
        private String orgCode = "";
        /** 机构名称（写入 receive 的 orgName）。 */
        private String orgName = "";
    }

    /** 推送 Worker 与 Mock 开关。 */
    @Getter
    @Setter
    public static class Push {
        private boolean mockEnabled = true;
        private int realtimeMaxDelaySeconds = 180;
        private int maxRetry = 5;
        /** 定时 drain 间隔毫秒（bridge {@code CrbPushDrainScheduler}）。 */
        private long drainIntervalMs = 30_000L;
        /** 定时 drain 目标租户；为空则跳过调度。 */
        private Long drainTenantId;
    }

    /** 云 LIS JDBC outbox 连接（默认关闭；启用后 bridge 轮询 crb_lab_outbox）。 */
    @Getter
    @Setter
    public static class Yunlis {
        private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yunlis?serverTimezone=GMT%2B8&useSSL=false&characterEncoding=utf8&allowPublicKeyRetrieval=true";
        private String username = "yunlis";
        private String password = "yunlis";
        private boolean enabled = false;
    }

    /** LIS 回传路径 B（yunlis outbox）配置。 */
    @Getter
    @Setter
    public static class Lab {
        /** 数据源：{@code whale-lis} 或 {@code external}。 */
        private String source = "external";
        /** outbox 轮询间隔秒。 */
        private int outboxPollSeconds = 60;
        /** Path B 入队租户（单租户场景必填；空则跳过消费）。 */
        private Long tenantId;
        private Yunlis yunlis = new Yunlis();
    }

    /** EMR id 生成策略（Mapper 阶段使用）。 */
    @Getter
    @Setter
    public static class Id {
        private String patientPattern = "{orgCode}_{patientBusNo}";
        private boolean activityVersioned = false;
    }
}
