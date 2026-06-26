package com.openhis.chs.api;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 医保平台返回体。
 *
 * @author Whale CHS
 */
@Data
public class ResultBody {

    @JSONField(name = "infcode")
    private String infcode;
    @JSONField(name = "warnMsg")
    private String warnMsg;
    @JSONField(name = "err_msg")
    private String errMsg;
    @JSONField(name = "output")
    private String output;
}
