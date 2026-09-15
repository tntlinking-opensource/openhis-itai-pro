/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platform.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 进销存已落库 {@code request_json} 解析模型（补传成功后重建 {@code yb_inv_upload_trace}）。
 *
 * <p>3505/3506 追溯在 {@code selinfo}；3502 追溯在 {@code invinfo}。</p>
 */
@Data
@Accessors(chain = true)
public class InvUploadPlatformResult {

    /** 3505/3506 销售/退货节点（含拆零标志与 drugtracinfo）。 */
    private InvUploadSelinfo selinfo;

    /** 3502 库存变更节点（含拆零标志与 drugtracinfo）。 */
    private InvUploadInvinfo invinfo;

    /**
     * 3505/3506 报文中的 {@code selinfo} 节点。
     */
    @Data
    @Accessors(chain = true)
    public static class InvUploadSelinfo {

        /** 拆零标志（平台 {@code trdn_flag}：0 整盒 / 1 拆零）。 */
        @JSONField(name = "trdn_flag")
        private String trdnFlag;

        /** 销售/退货追溯码列表（平台 {@code drugtracinfo}）。 */
        private List<InvUploadDrugTrace> drugtracinfo;
    }

    /**
     * 3502 报文中的 {@code invinfo} 节点。
     */
    @Data
    @Accessors(chain = true)
    public static class InvUploadInvinfo {

        /** 拆零标志（平台 {@code trdn_flag}：0 整盒 / 1 拆零）。 */
        @JSONField(name = "trdn_flag")
        private String trdnFlag;

        /** 库存变更追溯码列表（平台 {@code drugtracinfo}）。 */
        private List<InvUploadDrugTrace> drugtracinfo;
    }

    /**
     * 单条药品追溯码（平台 {@code drug_trac_codg}）。
     */
    @Data
    @Accessors(chain = true)
    public static class InvUploadDrugTrace {

        /** 药品追溯码原文（写入 {@code yb_inv_upload_trace.drug_trac_codg}）。 */
        @JSONField(name = "drug_trac_codg")
        private String drugTracCodg;
    }
}
