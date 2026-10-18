/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 明细审核（31xx）配置。
 *
 * <p>{@code syscode} 必填（产品 Q12=C）：无全局默认，缺省时 Gate 失败。
 * 配置键：{@code zxplat.chs.audit.syscode}。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "zxplat.chs.audit")
public class ChsAuditProperties {

    /**
     * 发送方系统代码（3101 {@code syscode}）。
     * <p>院点必配；Command 显式传入时可覆盖本配置。</p>
     */
    private String syscode;

    /**
     * 默认医疗机构类型（medins_type）；主路径未传时回落，如 {@code A}。
     */
    private String defaultMedinsType = "A";

    /**
     * 默认医疗机构等级（medins_lv）；主路径未传时回落，如 {@code 11}。
     */
    private String defaultMedinsLv = "11";
}
