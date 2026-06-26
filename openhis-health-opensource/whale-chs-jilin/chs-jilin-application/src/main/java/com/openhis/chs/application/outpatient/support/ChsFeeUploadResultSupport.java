/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.support;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.settlement.support.FeeUploadResultIndexer;

import java.util.Collections;
import java.util.Map;

/** 2204/2301 费用上传平台 result 解析。 */
public final class ChsFeeUploadResultSupport {

    private ChsFeeUploadResultSupport() {
    }

    /**
     * @deprecated 使用 {@link FeeUploadResultIndexer#indexByFeedetlSn(ResultBody)}。
     */
    @Deprecated
    public static Map<String, JSONObject> indexByFeedetlSn(ResultBody body) {
        return FeeUploadResultIndexer.indexByFeedetlSn(body);
    }
}
