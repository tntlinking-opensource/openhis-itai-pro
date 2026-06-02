package com.openhis.whale.mrms.application.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchIcdRequest {
    private String keyword;
    private Integer limit;
}
