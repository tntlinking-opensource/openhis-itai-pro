/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302RowRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【3302】目录对照撤销入参组装。 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class Catalog3302InputAssembler {

    /**
     * 组装 3302 单行撤销 input.data。
     *
     * @param fixmedinsCode 定点医药机构编号
     * @param row 待撤销对照行（fixmedins_hilist_id + list_type + med_list_codg）
     * @return 平台 input JSON
     */
    public JSONObject assemble(String fixmedinsCode, RevokeCatalog3302RowRequest row) {
        JSONObject data = new JSONObject();
        data.put("fixmedins_code", fixmedinsCode.trim());
        data.put("fixmedins_hilist_id", row.getFixmedinsHilistId());
        data.put("list_type", row.getListType());
        data.put("med_list_codg", row.getMedListCodg());
        JSONObject root = new JSONObject();
        root.put("data", data);
        return root;
    }

    /** Gate：3302 撤销行三要素必填。 */
    public void validateRow(RevokeCatalog3302RowRequest row) {
        if (row == null) {
            throw new IllegalArgumentException("3302 撤销行不能为空");
        }
        if (StringUtils.isBlank(row.getFixmedinsHilistId())) {
            throw new IllegalArgumentException("fixmedins_hilist_id 不能为空");
        }
        if (StringUtils.isBlank(row.getListType())) {
            throw new IllegalArgumentException("list_type 不能为空");
        }
        if (StringUtils.isBlank(row.getMedListCodg())) {
            throw new IllegalArgumentException("med_list_codg 不能为空");
        }
    }
}
