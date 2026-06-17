package com.openhis.whale.report.application.binding.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PrintBindingConfigDto {

    private List<PrintBindingItemDto> items = new ArrayList<>();
}
