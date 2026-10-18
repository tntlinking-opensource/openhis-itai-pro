-- 已落地库手工执行：体检指引单打印模板（对应 peis/V5）

INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    329000000000000002,
    1,
    NULL,
    'PT202608200001',
    '体检指引单',
    'published',
    1,
    '{"schemaVersion":1,"engine":"Print","panels":[{"name":"1","index":0,"width":210,"height":297,"paperType":"A4","paperFooter":575.4,"paperHeader":0,"printElements":[{"options":{"top":16,"left":28.3,"field":"hospitalName","width":539.4,"height":24,"fontSize":16,"hideTitle":true,"textAlign":"center","fontWeight":"bold","testData":"示例医院"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":44,"left":28.3,"title":"体检指引单","width":539.4,"height":18,"fontSize":13,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":72,"left":28.3,"field":"patientName","title":"姓名","width":120,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"姓名"}},{"options":{"top":72,"left":160,"field":"genderEnumText","title":"性别","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"性别"}},{"options":{"top":72,"left":252,"field":"ageText","title":"年龄","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"年龄"}},{"options":{"top":92,"left":28.3,"field":"busNo","title":"流水号","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"流水号"}},{"options":{"top":92,"left":240,"field":"registeredAtText","title":"登记时间","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"登记时间"}},{"options":{"top":112,"left":28.3,"field":"packageName","title":"套餐","width":400,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"套餐"}},{"options":{"top":136,"left":28.3,"width":539.4,"height":1,"borderWidth":1},"printElementType":{"type":"hline","title":"横线"}},{"options":{"top":148,"left":28.3,"title":"体检项目","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":164,"left":28.3,"field":"guideLines","width":539.4,"height":320,"fontSize":9,"textAlign":"center","lineHeight":14,"tableBorder":"border","tableHeaderBorder":"border","tableHeaderRepeat":"first","tableBodyRowBorder":"border","tableBodyRowHeight":22,"tableBodyCellBorder":"border","tableHeaderRowHeight":24,"tableHeaderCellBorder":"border","columns":[[{"title":"序号","field":"seqText","width":48,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"seqText"},{"title":"科室","field":"deptName","width":120,"colspan":1,"rowspan":1,"align":"left","checked":true,"columnId":"deptName"},{"title":"项目","field":"itemName","width":220,"colspan":1,"rowspan":1,"align":"left","checked":true,"columnId":"itemName"},{"title":"地点","field":"locationText","width":151.4,"colspan":1,"rowspan":1,"align":"left","checked":true,"columnId":"locationText"}]]},"printElementType":{"type":"table","title":"表格","editable":true,"columnResizable":true}}],"paperNumberContinue":true,"paperNumberDisabled":true,"watermarkOptions":{"width":200,"format":"YYYY-MM-DD HH:mm","height":200,"rotate":25,"content":"","fontSize":"14px","fillStyle":"rgba(184, 184, 184, 0.3)","timestamp":false},"panelLayoutOptions":{"layoutType":"column","paperStyle":"","paperWidth":0,"paperHeight":0,"layoutRowGap":0,"layoutColumnGap":0}}]}'::jsonb,
    1,
    '{"hospitalName":"示例医院","patientName":"张三","genderEnumText":"男","ageText":"45岁","busNo":"HN2026-08-2000001","registeredAtText":"2026-08-20 09:30","packageName":"2026常规体检套餐","guideLines":[{"seqText":"1","deptName":"检验科","itemName":"血常规","locationText":""},{"seqText":"2","deptName":"检验科","itemName":"肝功五项","locationText":""},{"seqText":"3","deptName":"影像科","itemName":"腹部彩超","locationText":""}]}'::jsonb,
    TIMESTAMP '2026-08-20 02:00:00+08',
    TIMESTAMP '2026-08-20 02:00:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'peis-guide-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
