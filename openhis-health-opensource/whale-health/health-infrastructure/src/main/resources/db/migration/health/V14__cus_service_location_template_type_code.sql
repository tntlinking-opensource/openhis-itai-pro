-- 取药科室模板：耗材种类中间维（对齐 adm_device_definition.type_code / device_type_code）
ALTER TABLE cus_service_location_template
    ADD COLUMN IF NOT EXISTS type_code varchar(64);

COMMENT ON COLUMN cus_service_location_template.type_code IS
    '耗材种类编码（术语 device_type_code）；空表示通配；药品行通常为空';
