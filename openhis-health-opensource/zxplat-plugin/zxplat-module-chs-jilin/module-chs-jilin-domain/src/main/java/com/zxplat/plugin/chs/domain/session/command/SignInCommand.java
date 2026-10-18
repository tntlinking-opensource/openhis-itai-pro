/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.session.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【9001】医保签到命令（P15）。
 *
 * <p><b>职责：</b>封装签到所需的契约编号、操作员与终端信息，供 {@code InsuranceSignInAccess} 出站。</p>
 * <p><b>调用方：</b>{@code InsuranceSignAppService}</p>
 * <p><b>架构边界：</b>domain 命令对象，不含 gateway {@code InvokeContext}。</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
@Data
@Accessors(chain = true)
public class SignInCommand {

    /** 契约业务编号（对应终端配置 busNo）。 */
    private String contractBusNo;

    /** 操作员 ID（平台 operatorId）。 */
    private String operatorId;

    /** 操作员姓名（平台 operatorName）。 */
    private String operatorName;

    /** 签到 MAC 地址（平台 signMac）。 */
    private String signMac;

    /** 签到 IP 地址（平台 signIp）。 */
    private String signIp;
}
