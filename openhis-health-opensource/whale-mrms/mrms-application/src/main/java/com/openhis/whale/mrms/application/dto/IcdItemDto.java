package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IcdItemDto {
    private Long id;
    private String code;
    private String name;
    private String pyStr;
    private String wbStr;
}
