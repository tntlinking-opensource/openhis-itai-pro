package com.openhis.chs.api.dto.national.medcas;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 4401 oprninfo 行。
 */
@Data
public class Chs4401OprnInfo {

    @JSONField(name = "ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    @JSONField(name = "mdtrt_sn")
    private String mdtrtSn;

    @JSONField(name = "fixmedins_code")
    private String fixmedinsCode;

    @JSONField(name = "oprn_oprt_date")
    private String oprnOprtDate;

    @JSONField(name = "oprn_oprt_code")
    private String oprnOprtCode;

    @JSONField(name = "oprn_oprt_name")
    private String oprnOprtName;

    @JSONField(name = "main_oprn_flag")
    private String mainOprnFlag;

    @JSONField(name = "vali_flag")
    private String valiFlag;
}
