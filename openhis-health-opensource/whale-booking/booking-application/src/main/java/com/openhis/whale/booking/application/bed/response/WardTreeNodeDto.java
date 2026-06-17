package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 病区树节点 DTO。
 */
@Getter
@Setter
public class WardTreeNodeDto {

    private Long sort;
    private String value;
    private String label;
    private List<WardTreeNodeDto> children;
}
