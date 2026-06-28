package com.openhis.chs.api.dto.national.medcas;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 4401 diseinfo 行。
 */
@Data
public class Chs4401DiseInfo {

    @JSONField(name = "ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    @JSONField(name = "mdtrt_sn")
    private String mdtrtSn;

    @JSONField(name = "fixmedins_code")
    private String fixmedinsCode;

    @JSONField(name = "ipt_patn_disediag_type_code")
    private String iptPatnDisediagTypeCode;

    @JSONField(name = "maindiag_flag")
    private String maindiagFlag;

    @JSONField(name = "diag_code")
    private String diagCode;

    @JSONField(name = "diag_name")
    private String diagName;

    @JSONField(name = "adm_cond_code")
    private String admCondCode;

    @JSONField(name = "vali_flag")
    private String valiFlag;
}
