package com.openhis.whale.report.application.layout.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LayoutDocument {

    private String pipeline = "page";
    private LayoutPage page = new LayoutPage();
    private List<LayoutItem> items = new ArrayList<>();
}
