package com.openhis.whale.report.application.design.response;

import lombok.Getter;

import java.util.List;

/** 设计器 layoutJson 校验结果。 */
@Getter
public class ValidateLayoutResultDto {

    /** 是否通过校验 */
    private final boolean valid;
    /** 错误信息列表，通过时为空 */
    private final List<String> errors;

    public ValidateLayoutResultDto(boolean valid, List<String> errors) {
        this.valid = valid;
        if (errors != null) {
            this.errors = List.copyOf(errors);
        } else {
            this.errors = List.of();
        }
    }
}
