package com.openhis.chs.api.dto.national.medcas;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * 【4401】住院病案首页信息上传根对象。
 */
@Data
public class Chs4401Input {

    private Chs4401BaseInfo baseinfo;

    private List<Chs4401DiseInfo> diseinfo;

    private List<Chs4401OprnInfo> oprninfo;

    private List<Chs4401IcuInfo> icuinfo;
}
