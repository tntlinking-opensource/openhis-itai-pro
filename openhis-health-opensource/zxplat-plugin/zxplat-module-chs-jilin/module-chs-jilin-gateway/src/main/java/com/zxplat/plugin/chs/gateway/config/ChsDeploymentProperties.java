/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CHS 部署模式：standalone=true 表示 chs-server 独立进程，不依赖 HIS 登录用户 */
@Data
@Component
@ConfigurationProperties(prefix = "zxplat.chs.deployment")
public class ChsDeploymentProperties {

    /** embedded | remote（health 侧配置；chs-server 内网关恒为 local） */
    private String mode = "embedded";

    /**
     * 独立部署（{@code ChsServerApplication}）：签到 OAuth 固定 admin/admin1，不读当前登录用户 */
    private boolean standalone = false;
}
