/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302RowRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Catalog3302InputAssemblerTest {

    private final Catalog3302InputAssembler assembler = new Catalog3302InputAssembler();

    @Test
    void assemble_wrapsSingleRowInDataNode() {
        RevokeCatalog3302RowRequest row = new RevokeCatalog3302RowRequest()
            .setFixmedinsHilistId("MED001")
            .setListType("101")
            .setMedListCodg("X123456789");

        JSONObject root = assembler.assemble("H22019900001", row);

        JSONObject data = root.getJSONObject("data");
        assertThat(data.getString("fixmedins_code")).isEqualTo("H22019900001");
        assertThat(data.getString("fixmedins_hilist_id")).isEqualTo("MED001");
        assertThat(data.getString("list_type")).isEqualTo("101");
        assertThat(data.getString("med_list_codg")).isEqualTo("X123456789");
    }
}
