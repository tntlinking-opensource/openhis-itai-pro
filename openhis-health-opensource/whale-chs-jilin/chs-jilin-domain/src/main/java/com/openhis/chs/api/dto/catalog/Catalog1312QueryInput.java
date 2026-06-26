package com.openhis.chs.api.dto.catalog;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 【1312】医保目录信息查询入参（data 节点）。
 */
@Data
@Builder
public class Catalog1312QueryInput {

    @JSONField(name = "query_date")
    private String queryDate;

    @JSONField(name = "hilist_code")
    private String hilistCode;

    @JSONField(name = "insu_admdvs")
    private String insuAdmdvs;

    @JSONField(name = "begndate")
    private String begndate;

    @JSONField(name = "hilist_name")
    private String hilistName;

    @JSONField(name = "wubi")
    private String wubi;

    @JSONField(name = "pinyin")
    private String pinyin;

    @JSONField(name = "chrgitm_lv")
    private String chrgitmLv;

    @JSONField(name = "lmt_used_flag")
    private String lmtUsedFlag;

    @JSONField(name = "list_type")
    private String listType;

    @JSONField(name = "vali_flag")
    private String valiFlag;

    /** 增量游标：查询该时间点之后有更新的目录（吉林平台必填） */
    @JSONField(name = "updt_time")
    private String updtTime;

    @JSONField(name = "page_num")
    private Integer pageNum;

    @JSONField(name = "page_size")
    private Integer pageSize;
}
