package com.openhis.chs.api.dto.national.medcas;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 4401 icuinfo 行。
 */
@Data
public class Chs4401IcuInfo {

    @JSONField(name = "icu_codeid")
    private String icuCodeid;

    @JSONField(name = "inpool_icu_time")
    private String inpoolIcuTime;

    @JSONField(name = "out_icu_time")
    private String outIcuTime;

    @JSONField(name = "vali_flag")
    private String valiFlag;
}
