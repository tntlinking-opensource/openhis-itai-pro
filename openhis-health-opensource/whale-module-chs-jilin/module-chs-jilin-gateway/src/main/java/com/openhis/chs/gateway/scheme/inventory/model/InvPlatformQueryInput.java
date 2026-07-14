/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3508–3513】平台进销存查询通用 input */
public class InvPlatformQueryInput extends ChsDataInput<InvPlatformQueryInput.InvPlatformQueryData> {

    /** 3508–3513 input.data */
    @Data
    @Accessors(chain = true)
    public static class InvPlatformQueryData {

        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;

        @JSONField(name = "medins_list_codg")
        private String medinsListCodg;

        @JSONField(name = "fixmedins_bchno")
        private String fixmedinsBchno;

        private String begndate;

        private String enddate;

        @JSONField(name = "med_list_codg")
        private String medListCodg;

        @JSONField(name = "medins_prod_sel_no")
        private String medinsProdSelNo;

        @JSONField(name = "medins_prod_purc_no")
        private String medinsProdPurcNo;

        @JSONField(name = "medins_prod_inv_no")
        private String medinsProdInvNo;

        @JSONField(name = "psn_no")
        private String psnNo;

        private String certno;

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;

        @JSONField(name = "drug_trac_codg")
        private String drugTracCodg;

        @JSONField(name = "vali_flag")
        private String valiFlag;
    }
}
