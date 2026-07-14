/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.contract.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保终端配置子命令。
 */
@Getter
@Setter
public class ContractTerminalConfigCommand {
    /** client主键。 */
    private String clientId;
    /** clientsecret。 */
    private String clientSecret;
    /** username。 */
    private String username;
    /** password。 */
    private String password;
    /** cliprvkey。 */
    private String cliPrvKey;
    /** clipubkey。 */
    private String cliPubKey;
    /** serverpubkey。 */
    private String serverPubKey;
    /** scope。 */
    private String scope;
    /** grant类型。 */
    private String grantType;
    /** fixmedins编码。 */
    private String fixmedinsCode;
    /** fixmedins名称。 */
    private String fixmedinsName;
}
