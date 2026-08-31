-- 通用报表列表打印模板（库存明细等共用壳；列由前端按勾选覆盖）
-- scenario_code = report-list-print；bus_no = PT202608310001
-- 可重复执行（按 id upsert）

INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    331800190001000041,
    1,
    NULL,
    'PT202608310001',
    '报表列表',
    'published',
    1,
    $print_json$
{
  "engine": "Print",
  "panels": [
    {
      "name": "1",
      "index": 0,
      "width": 297,
      "height": 210,
      "paperType": "A4",
      "paperFooter": 580,
      "paperHeader": 54,
      "printElements": [
        {
          "options": {
            "top": 8,
            "left": 15,
            "field": "hospitalName",
            "title": "医院",
            "width": 810,
            "height": 22,
            "fontSize": 16,
            "hideTitle": true,
            "textAlign": "center",
            "fontWeight": "700",
            "textContentVerticalAlign": "middle"
          },
          "printElementType": {
            "type": "text",
            "title": "医院"
          }
        },
        {
          "options": {
            "top": 30,
            "left": 15,
            "field": "reportTitle",
            "title": "报表标题",
            "width": 810,
            "height": 20,
            "fontSize": 14,
            "hideTitle": true,
            "textAlign": "center",
            "fontWeight": "700",
            "textContentVerticalAlign": "middle"
          },
          "printElementType": {
            "type": "text",
            "title": "报表标题"
          }
        },
        {
          "options": {
            "top": 54,
            "left": 15,
            "field": "rows",
            "width": 810,
            "height": 40,
            "columns": [
              [
                {
                  "align": "center",
                  "field": "seqNo",
                  "title": "序号",
                  "width": 40,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "productBusNo",
                  "title": "编码",
                  "width": 90,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "name",
                  "title": "项目名称",
                  "width": 140,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "totalVolume",
                  "title": "规格",
                  "width": 90,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "lotNumber",
                  "title": "批号",
                  "width": 80,
                  "halign": "center"
                },
                {
                  "align": "right",
                  "field": "packageStockText",
                  "title": "库存(包装)",
                  "width": 80,
                  "halign": "center"
                },
                {
                  "align": "right",
                  "field": "splitStockText",
                  "title": "库存(最小)",
                  "width": 80,
                  "halign": "center"
                },
                {
                  "align": "right",
                  "field": "lotCostPrice",
                  "title": "进价",
                  "width": 70,
                  "halign": "center"
                },
                {
                  "align": "right",
                  "field": "lotSalePrice",
                  "title": "售价",
                  "width": 70,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "expirationDate",
                  "title": "效期",
                  "width": 80,
                  "halign": "center"
                },
                {
                  "align": "center",
                  "field": "warehouseName",
                  "title": "库房",
                  "width": 90,
                  "halign": "center"
                }
              ]
            ],
            "fontSize": 8,
            "textAlign": "center",
            "tableBorder": "border",
            "tableHeaderRepeat": "page",
            "tableHeaderBackground": "#ffffff"
          },
          "printElementType": {
            "type": "table",
            "title": "明细表"
          }
        }
      ],
      "paperNumberContinue": true,
      "paperNumberDisabled": false
    }
  ],
  "schemaVersion": 1
}
$print_json$::jsonb,
    1,
    $sample$
{
  "hospitalName": "示例医院",
  "reportTitle": "药库库存明细",
  "rows": [
    {
      "seqNo": "1",
      "productBusNo": "YP001",
      "name": "阿莫西林胶囊",
      "totalVolume": "0.25g*24粒",
      "lotNumber": "L20260101",
      "packageStockText": "10盒",
      "splitStockText": "240片",
      "lotCostPrice": "12.50 元/盒",
      "lotSalePrice": "15.60 元/盒",
      "expirationDate": "2028-01-01",
      "warehouseName": "中心药库"
    },
    {
      "seqNo": "2",
      "productBusNo": "YP002",
      "name": "0.9%氯化钠注射液",
      "totalVolume": "250ml",
      "lotNumber": "L20260215",
      "packageStockText": "20袋",
      "splitStockText": "20袋",
      "lotCostPrice": "3.80 元/袋",
      "lotSalePrice": "4.75 元/袋",
      "expirationDate": "2027-06-30",
      "warehouseName": "中心药库"
    }
  ],
  "summaryItems": [
    { "label": "进价合计", "value": "201.00" },
    { "label": "售价合计", "value": "251.00" }
  ]
}
$sample$::jsonb,
    TIMESTAMP '2026-08-31 00:00:00+08',
    TIMESTAMP '2026-08-31 09:30:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'report-list-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";
