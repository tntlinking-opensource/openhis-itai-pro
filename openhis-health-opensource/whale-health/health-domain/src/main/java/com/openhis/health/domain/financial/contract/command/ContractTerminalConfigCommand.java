/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
