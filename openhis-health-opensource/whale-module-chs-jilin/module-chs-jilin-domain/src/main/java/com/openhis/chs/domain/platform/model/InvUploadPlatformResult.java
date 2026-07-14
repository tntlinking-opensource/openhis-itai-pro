/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platform.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 进销存上传请求 JSON 中 selinfo 节点（3505/3506 追溯码重建） */
@Data
@Accessors(chain = true)
public class InvUploadPlatformResult {

    private InvUploadSelinfo selinfo;

    @Data
    @Accessors(chain = true)
    public static class InvUploadSelinfo {

        @JSONField(name = "trdn_flag")
        private String trdnFlag;

        private List<InvUploadDrugTrace> drugtracinfo;
    }

    @Data
    @Accessors(chain = true)
    public static class InvUploadDrugTrace {

        @JSONField(name = "drug_trac_codg")
        private String drugTracCodg;
    }
}
