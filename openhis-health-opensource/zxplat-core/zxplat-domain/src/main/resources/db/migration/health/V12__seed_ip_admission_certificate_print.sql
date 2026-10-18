-- 住院证打印模板（A4 竖版）
-- scenario_code = ip-admissionCertificate-print；bus_no = PT202610110002
-- 门诊办理入院 / 住院登记；PrintData 见前端 inpatient-admission-certificate mapper
INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    339900110000000262,
    1,
    NULL,
    'PT202610110002',
    '住院证',
    'published',
    1,
    '{"engine":"Print","schemaVersion":1,"panels":[{"name":"1","index":0,"width":210,"height":297,"paperType":"A4","paperHeader":0,"paperFooter":800,"paperNumberContinue":false,"paperNumberDisabled":true,"watermarkOptions":{"width":200,"format":"YYYY-MM-DD HH:mm","height":200,"rotate":25,"content":"","fontSize":"14px","fillStyle":"rgba(184, 184, 184, 0.3)","timestamp":false},"panelLayoutOptions":{"layoutType":"column","paperStyle":"","paperWidth":0,"paperHeight":0,"layoutRowGap":0,"layoutColumnGap":0},"printElements":[{"options":{"top":24,"left":24,"field":"hospitalName","width":546,"height":28,"fontSize":22,"testData":"南关国基医院","hideTitle":true,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"医院"}},{"options":{"top":56,"left":24,"field":"documentTitle","width":546,"height":24,"fontSize":18,"testData":"住院证","hideTitle":true,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"标题"}},{"options":{"top":88,"left":24,"field":"departmentName","title":"入院科室","width":280,"height":18,"fontSize":12,"testData":"住院外科"},"printElementType":{"type":"text","title":"入院科室"}},{"options":{"top":88,"left":340,"field":"inpatientNo","title":"住院号","width":230,"height":18,"fontSize":12,"testData":"26103439"},"printElementType":{"type":"text","title":"住院号"}},{"options":{"top":116,"left":24,"field":"certificateTable","leftSpaceRemoved":false,"width":546,"height":360,"fontSize":12,"hideTitle":true,"lineHeight":18,"formatter":"function(title,value,options,printData){function esc(s){return String(s==null?'''':s).replace(/&/g,''&amp;'').replace(/</g,''&lt;'').replace(/>/g,''&gt;'').replace(/\"/g,''&quot;'');}var d=printData||{};var c=''border:1px solid #000;padding:10px 12px;vertical-align:middle;font-size:12pt;'';var l=''font-weight:bold;'';var h='''';h+=''<table style=\"width:100%;border-collapse:collapse;border:2px solid #000;table-layout:fixed;\">'';h+=''<tr><td style=\"''+c+''width:34%\"><span style=\"''+l+''\">姓名：</span>''+esc(d.patientName)+''</td><td style=\"''+c+''width:33%\"><span style=\"''+l+''\">性别：</span>''+esc(d.genderText)+''</td><td style=\"''+c+''width:33%\"><span style=\"''+l+''\">年龄：</span>''+esc(d.ageText)+''</td></tr>'';h+=''<tr><td colspan=\"2\" style=\"''+c+''\"><span style=\"''+l+''\">住址：</span>''+esc(d.address)+''</td><td style=\"''+c+''\"><span style=\"''+l+''\">电话：</span>''+esc(d.phone)+''</td></tr>'';h+=''<tr><td colspan=\"3\" style=\"''+c+''height:72px;vertical-align:top;\"><div style=\"''+l+''\">入院诊断：</div><div style=\"margin-top:6px;min-height:40px;white-space:pre-wrap;\">''+esc(d.diagnosisText)+''</div></td></tr>'';h+=''<tr><td colspan=\"2\" style=\"''+c+''\"><span style=\"''+l+''\">病情：</span>''+esc(d.conditionText)+''</td><td style=\"''+c+''\"><span style=\"''+l+''\">入院日期：</span>''+esc(d.admissionTimeText)+''</td></tr>'';h+=''<tr><td colspan=\"2\" style=\"''+c+''\"><span style=\"''+l+''\">医疗付费方式：</span>''+esc(d.paymentMethodText)+''</td><td style=\"''+c+''\"><span style=\"''+l+''\">医师签名：</span></td></tr>'';h+=''<tr><td colspan=\"2\" style=\"''+c+''\"><span style=\"''+l+''\">预交医药金额：</span>''+esc(d.prepayAmountText)+''</td><td style=\"''+c+''\"><span style=\"''+l+''\">经办人：</span></td></tr>'';h+=''</table>'';return h;}"},"printElementType":{"type":"longText","title":"表格"}}]}]}'::jsonb,
    1,
    '{"hospitalName":"南关国基医院","documentTitle":"住院证","departmentName":"住院外科","inpatientNo":"26103439","patientName":"庄海玲","genderText":"女","ageText":"51","address":"吉林省榆树市保寿镇新合村桦树屯5组","phone":"15843150176","diagnosisText":"胆囊息肉，胆囊炎","conditionText":"一般","admissionTimeText":"2026-10-10 16:17","paymentMethodText":"居民医保","prepayAmountText":"5,000.00 元","certificateTable":""}'::jsonb,
    TIMESTAMP '2026-10-11 13:00:00+08',
    TIMESTAMP '2026-10-11 13:00:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'ip-admissionCertificate-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
