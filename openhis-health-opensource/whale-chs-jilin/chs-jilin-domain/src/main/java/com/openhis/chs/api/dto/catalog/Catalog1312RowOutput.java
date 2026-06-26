package com.openhis.chs.api.dto.catalog;

import lombok.Data;

/**
 * 【1312】单行目录出参（解析 output 数组元素）。
 */
@Data
public class Catalog1312RowOutput {

    private String hilistCode;
    private String hilistName;
    private String insuAdmdvs;
    private String begndate;
    private String enddate;
    private String chrgitmLv;
    private String lmtUsedFlag;
    private String listType;
    private String pinyin;
    private String wubi;
    private String valiFlag;
    private String updtTime;
}
