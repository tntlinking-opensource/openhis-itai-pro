/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port;

import com.openhis.whale.chs.jilin.port.model.YbPersonInfoRequest;
import com.openhis.whale.chs.jilin.port.model.YbPersonInfoResponse;

/** 1101 人员信息 Port。 */
public interface InsurancePersonPort {

    YbPersonInfoResponse getPersonInfo(YbPersonInfoRequest request);
}
