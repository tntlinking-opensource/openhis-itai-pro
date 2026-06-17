package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 主数据下拉选项（Port 出站契约）。
 */
@Getter
@Setter
public class MasterDataOption {

    /** 排序号。 */
    private Long sort;

    /** 选项值（前端绑定 value，统一 String）。 */
    private String value;

    /** 显示文案。 */
    private String label;
}
