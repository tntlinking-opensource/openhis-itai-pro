/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.client;

import com.openhis.whale.chs.jilin.contracts.command.DispenseTraceGateCommand;

import java.util.List;

/** 发药前追溯码门控 Port（§4.8.1 类型 D + block-settle-without-trace）。 */
public interface ChsDispenseTraceGateClient {

    void assertTraceAllowedForPrepare(DispenseTraceGateCommand command, List<String> submittedTraceCodes);

    void assertTraceAllowedForDispense(DispenseTraceGateCommand command);
}
