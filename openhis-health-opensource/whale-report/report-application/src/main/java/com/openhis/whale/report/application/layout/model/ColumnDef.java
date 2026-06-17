package com.openhis.whale.report.application.layout.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnDef {

    private String field;
    private String header;
    private Float widthMm;
    /** 列内容水平对齐：left / center / right */
    private String align;
}
