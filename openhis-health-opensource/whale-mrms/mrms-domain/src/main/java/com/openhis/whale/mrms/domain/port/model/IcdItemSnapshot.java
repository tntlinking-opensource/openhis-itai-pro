package com.openhis.whale.mrms.domain.port.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IcdItemSnapshot {
    private Long id;
    private String code;
    private String name;
    private String pyStr;
    private String wbStr;
}
