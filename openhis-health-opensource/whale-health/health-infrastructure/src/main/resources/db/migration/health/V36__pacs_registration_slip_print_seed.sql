-- PACS 登记条打印模板种子（序号 60 REG-05）
-- scenario_code = pacs-registration-slip-print；与 PacsPrintScenarioConstants.REGISTRATION_SLIP_PRINT 对齐

INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    329000000000000010,
    1,
    NULL,
    'PT202609220010',
    '检查登记条',
    'published',
    1,
    '{"schemaVersion":1,"engine":"Print","panels":[{"name":"1","index":0,"width":100,"height":60,"paperType":"自定义","paperFooter":0,"paperHeader":0,"printElements":[{"options":{"top":8,"left":8,"field":"accessionNumber","title":"检查号","width":84,"height":14,"fontSize":12,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"检查号"}},{"options":{"top":24,"left":8,"field":"patientName","title":"姓名","width":50,"height":12,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"姓名"}},{"options":{"top":24,"left":60,"field":"genderEnumText","title":"性别","width":30,"height":12,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"性别"}},{"options":{"top":38,"left":8,"field":"modalityCode","title":"模态","width":40,"height":12,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"模态"}},{"options":{"top":38,"left":50,"field":"organizationName","title":"科室","width":42,"height":12,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"科室"}}],"paperNumberContinue":true,"paperNumberDisabled":true}]}'::jsonb,
    1,
    '{"accessionNumber":"ACC202609220001","patientName":"张三","genderEnumText":"男","ageText":"36岁","organizationName":"影像科","modalityCode":"CT","bodySiteCode":"HEAD","scheduledTimeText":"2026-09-22 09:30:00","statusEnum":"scheduled"}'::jsonb,
    TIMESTAMP '2026-09-22 09:00:00+08',
    TIMESTAMP '2026-09-22 09:00:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'pacs-registration-slip-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
