-- PEIS 体检总检报告打印模板（scenario_code = peis-report-print）
INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    329000000000000001,
    1,
    NULL,
    'PT202607250001',
    '体检总检报告',
    'published',
    1,
    '{"schemaVersion":1,"engine":"Print","panels":[{"name":"1","index":0,"width":210,"height":297,"paperType":"A4","paperFooter":575.4,"paperHeader":0,"printElements":[{"options":{"top":16,"left":28.3,"field":"hospitalName","width":539.4,"height":24,"fontSize":16,"hideTitle":true,"textAlign":"center","fontWeight":"bold","testData":"示例医院"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":44,"left":28.3,"title":"体检总检报告","width":539.4,"height":18,"fontSize":13,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":72,"left":28.3,"field":"patientName","title":"姓名","width":120,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"姓名"}},{"options":{"top":72,"left":160,"field":"genderEnumText","title":"性别","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"性别"}},{"options":{"top":72,"left":252,"field":"ageText","title":"年龄","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"年龄"}},{"options":{"top":92,"left":28.3,"field":"busNo","title":"流水号","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"流水号"}},{"options":{"top":92,"left":240,"field":"checkDateText","title":"定稿时间","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"定稿时间"}},{"options":{"top":116,"left":28.3,"width":539.4,"height":1,"borderWidth":1},"printElementType":{"type":"hline","title":"横线"}},{"options":{"top":124,"left":28.3,"title":"综述","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":140,"left":28.3,"field":"summaryText","width":539.4,"height":60,"fontSize":10,"textAlign":"left","lineHeight":14},"printElementType":{"type":"longText","title":"长文"}},{"options":{"top":208,"left":28.3,"title":"结论","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":224,"left":28.3,"field":"conclusionText","width":539.4,"height":60,"fontSize":10,"textAlign":"left","lineHeight":14},"printElementType":{"type":"longText","title":"长文"}},{"options":{"top":292,"left":28.3,"title":"检查结果","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":308,"left":28.3,"field":"observationLines","width":539.4,"height":200,"fontSize":9,"textAlign":"center","lineHeight":14,"tableBorder":"border","tableHeaderBorder":"border","tableHeaderRepeat":"first","tableBodyRowBorder":"border","tableBodyRowHeight":20,"tableBodyCellBorder":"border","tableHeaderRowHeight":22,"tableHeaderCellBorder":"border","columns":[[{"title":"项目","field":"itemName","width":160,"colspan":1,"rowspan":1,"align":"left","checked":true,"columnId":"itemName"},{"title":"结果","field":"resultText","width":120,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"resultText"},{"title":"单位","field":"unitText","width":80,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"unitText"},{"title":"参考范围","field":"refRangeText","width":179.4,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"refRangeText"}]]},"printElementType":{"type":"table","title":"表格","editable":true,"columnResizable":true}}],"paperNumberContinue":true,"paperNumberDisabled":true,"watermarkOptions":{"width":200,"format":"YYYY-MM-DD HH:mm","height":200,"rotate":25,"content":"","fontSize":"14px","fillStyle":"rgba(184, 184, 184, 0.3)","timestamp":false},"panelLayoutOptions":{"layoutType":"column","paperStyle":"","paperWidth":0,"paperHeight":0,"layoutRowGap":0,"layoutColumnGap":0}}]}'::jsonb,
    1,
    '{"hospitalName":"示例医院","patientName":"张三","genderEnumText":"男","ageText":"25岁","busNo":"PE202607250001","checkDateText":"2026-07-25 10:30","summaryText":"各科室检查未见明显异常。","conclusionText":"体检合格。","observationLines":[{"itemName":"身高","resultText":"175","unitText":"cm","refRangeText":""},{"itemName":"体重","resultText":"70","unitText":"kg","refRangeText":""},{"itemName":"血压","resultText":"120/80","unitText":"mmHg","refRangeText":""}]}'::jsonb,
    TIMESTAMP '2026-07-25 22:00:00+08',
    TIMESTAMP '2026-07-25 22:00:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'peis-report-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
