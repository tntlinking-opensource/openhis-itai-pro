package com.openhis.whale.report.application.layout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 排版元素落格（mm，纸面左上角原点）。
 */
@Getter
@Setter
public class LayoutBox {

    /** 左上角 X（mm） */
    @JsonProperty("xMm")
    private Float xMm;
    /** 左上角 Y（mm） */
    @JsonProperty("yMm")
    private Float yMm;
    /** 宽度（mm） */
    private Float widthMm;
    /** 高度（mm） */
    private Float heightMm;
    /** 溢出策略 clip / shrinkFont */
    private String overflow = "clip";
    /** 表格行高（mm，可选） */
    private Float rowHeightMm;
    /** 表格最大行槽（处方药品表固定 5 行 + 空位斜线） */
    private Integer maxRows;
}
