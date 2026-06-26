package com.openhis.chs.api.dto.session;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 【9001】签到 signIn 节点。
 */
@Data
@Builder
public class Sign9001Input {

    @JSONField(name = "opter_no")
    private String opterNo;

    private String mac;

    private String ip;
}
