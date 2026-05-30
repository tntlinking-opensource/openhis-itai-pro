package com.openhis.whale.mrms.domain.port.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TransferLineSnapshot {
    private LocalDateTime transferAt;
    private String fromDeptCode;
    private String fromDeptName;
    private String toDeptCode;
    private String toDeptName;
}
