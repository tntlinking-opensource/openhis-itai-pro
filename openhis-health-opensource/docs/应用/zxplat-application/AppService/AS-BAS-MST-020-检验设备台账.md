# AS-BAS-MST-020 检验设备台账

> **编码**：AS-BAS-MST-020 · **菜单**：基础数据 · **菜单名**：设备管理 · **路径**：`/basic/labdevice`  
> **AppService**：`LabDeviceAppService` → `/api/app/labDevice/*`

## 1. 接口

| IF | 方法 | 说明 |
|---|---|---|
| IF-LAD-01 | init | 分类/状态/归属科室/生产厂家下拉 |
| IF-LAD-02 | page | 分页列表（支持 keyword、statusEnum、categoryCode、**orgId**） |
| IF-LAD-03 | getLabDevice | 详情 |
| IF-LAD-04 | createLabDevice / updateLabDevice | 增改 |
| IF-LAD-05 | deleteLabDevice | 逻辑删 |
| IF-LAD-06 | enableLabDevice / disableLabDevice | 启停（含 batchEnable/batchDisable） |
| IF-LAD-07 | search | 关键字检索 |

## 2. init 出参要点

- `categoryCodeOptions`：术语 `adm_device_category_code`
- `statusEnumOptions`：`PublicationStatus`
- `organizationOptions`：责任科室树
- `manufacturerOptions`：生产厂家（cus_supplier type=MANUFACTURER）

## 3. 领域

`DM-ADM-015 LabDevice`

## 4. Gap

无（Phase 1 不含合管开嘱带出）。
