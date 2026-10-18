-- 住院退药单（A4 竖版，一名患者一张）
-- scenario_code = ip-returnMedicine-print；bus_no = PT202610140001
-- 药房住院退药页「退药打印」；PrintData 见前端 inpatient-return-medicine mapper
INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    339900110000000264,
    1,
    NULL,
    'PT202610140001',
    '住院退药单',
    'published',
    1,
    '{"engine":"Print","schemaVersion":1,"panels":[{"name":"1","index":0,"width":210,"height":297,"paperType":"A4","paperHeader":142,"paperFooter":800,"paperNumberContinue":false,"paperNumberDisabled":true,"watermarkOptions":{"width":200,"format":"YYYY-MM-DD HH:mm","height":200,"rotate":25,"content":"","fontSize":"14px","fillStyle":"rgba(184, 184, 184, 0.3)","timestamp":false},"panelLayoutOptions":{"layoutType":"column","paperStyle":"","paperWidth":0,"paperHeight":0,"layoutRowGap":0,"layoutColumnGap":0},"printElements":[{"options":{"top":16,"left":24,"field":"hospitalName","width":546,"height":20,"fontSize":16,"testData":"南关国基医院","hideTitle":true,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"医院"}},{"options":{"top":40,"left":24,"field":"documentTitle","width":546,"height":22,"fontSize":18,"testData":"住院退药单","hideTitle":true,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"标题"}},{"options":{"top":66,"left":24,"width":546,"height":1.2,"borderWidth":1.2},"printElementType":{"type":"hline","title":"标题线"}},{"options":{"top":74,"left":24,"field":"patientName","title":"姓名","width":170,"height":16,"fontSize":11,"fontWeight":"bold","testData":"高云及"},"printElementType":{"type":"text","title":"姓名"}},{"options":{"top":74,"left":200,"field":"genderAgeText","title":"性别/年龄","width":180,"height":16,"fontSize":11,"testData":"男性 / 37岁"},"printElementType":{"type":"text","title":"性别/年龄"}},{"options":{"top":74,"left":390,"field":"hospitalizationNo","title":"住院号","width":180,"height":16,"fontSize":11,"testData":"ZY2026-10-130015"},"printElementType":{"type":"text","title":"住院号"}},{"options":{"top":94,"left":24,"field":"wardName","title":"病区","width":220,"height":16,"fontSize":11,"testData":"住院外科病区"},"printElementType":{"type":"text","title":"病区"}},{"options":{"top":94,"left":250,"field":"bedName","title":"床位","width":130,"height":16,"fontSize":11,"testData":"507-22"},"printElementType":{"type":"text","title":"床位"}},{"options":{"top":94,"left":390,"field":"feeNatureText","title":"费用性质","width":180,"height":16,"fontSize":11,"testData":"自费"},"printElementType":{"type":"text","title":"费用性质"}},{"options":{"top":114,"left":24,"field":"statusText","title":"单据状态","width":200,"height":16,"fontSize":11,"testData":"待退药"},"printElementType":{"type":"text","title":"单据状态"}},{"options":{"top":114,"left":250,"field":"printedAtText","title":"打印时间","width":320,"height":16,"fontSize":11,"testData":"2026-10-14 15:08"},"printElementType":{"type":"text","title":"打印时间"}},{"options":{"top":132,"left":24,"width":546,"height":1,"borderWidth":1},"printElementType":{"type":"hline","title":"表头线"}},{"options":{"top":142,"left":24,"field":"drugRows","width":546,"height":640,"fontSize":10,"textAlign":"center","tableHeaderRepeat":"page","tableHeaderRowHeight":24,"tableBodyRowHeight":22,"columns":[[{"title":"序号","field":"seqText","width":40,"align":"center","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"seqText"},{"title":"药品名称","field":"drugName","width":180,"align":"left","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"drugName"},{"title":"批号","field":"lotNumber","width":90,"align":"left","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"lotNumber"},{"title":"退药数量","field":"quantityText","width":64,"align":"center","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"quantityText"},{"title":"单位","field":"unitText","width":44,"align":"center","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"unitText"},{"title":"开单医生","field":"doctorName","width":72,"align":"center","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"doctorName"},{"title":"状态","field":"statusText","width":56,"align":"center","halign":"center","colspan":1,"rowspan":1,"checked":true,"columnId":"statusText"}]]},"printElementType":{"type":"table","title":"退药明细"}}]}]}'::jsonb,
    1,
    '{"hospitalName":"南关国基医院","documentTitle":"住院退药单","patientName":"高云及","genderAgeText":"男性 / 37岁","hospitalizationNo":"ZY2026-10-130015","wardName":"住院外科病区","bedName":"507-22","feeNatureText":"自费","statusText":"待退药","printedAtText":"2026-10-14 15:08","drugRows":[{"seqText":"1","drugName":"注射用头孢噻肟钠（金鸿）","lotNumber":"A260709","quantityText":"2","unitText":"支","doctorName":"王勇钧","statusText":"待退药"},{"seqText":"2","drugName":"100ml氯化钠注射液","lotNumber":"260421T03","quantityText":"1","unitText":"瓶","doctorName":"王勇钧","statusText":"待退药"},{"seqText":"3","drugName":"吸入用布地奈德混悬液（四川）","lotNumber":"ZA26076","quantityText":"1","unitText":"支","doctorName":"王勇钧","statusText":"待退药"}]}'::jsonb,
    TIMESTAMP '2026-10-14 15:10:00+08',
    TIMESTAMP '2026-10-14 15:10:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'ip-returnMedicine-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "status" = EXCLUDED."status",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
