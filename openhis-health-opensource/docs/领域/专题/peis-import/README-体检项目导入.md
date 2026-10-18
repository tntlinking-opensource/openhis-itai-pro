# 老体检目录导入说明（v2）

## 文件

| 文件 | 说明 |
|------|------|
| `01-体检项目目录扩展方案.md` | 代码/字段方案 |
| `V20260819c__import_legacy_peis_catalog.sql` | 项目+明细+AOL（幂等） |
| `V20260819d__backfill_peis_org_id.sql` | 项目执行科室回填 |
| `V20260820a__import_legacy_peis_packages.sql` | **套餐 89 + 子项绑定** |
| `V20260820c__backfill_peis_activity_package_unit.sql` | PEIS 项目补齐包装单位=次(6) |
| `V20260820d__audit_peis_missing_sale_price.sql` | 审计：应收缺 SALE / 不收费项清单 |
| `gen_peis_catalog_import_v2.py` / `gen_peis_package_import.py` | 重生成脚本 |
| `体检套餐json.txt` / `体检套餐明细json.txt` | 老系统抓取源 |
| health `V11` / `V12` / `V14` | 扩展字段 + 列表索引 + 包装单位回填 |

## 口径

- 原价 `dj`/`je` → SALE（不用优惠价 `yhj`/`yj`）
- **有原价（dj>0）一律可收费并落 SALE**（即使老库 `isfy=否`）；`dj=0` 且 `isfy=否` 才为不收费
- `iswjfw=1` → `type_enum=6` 外检
- `lx=PACS模式` → `type_enum=2` 检查
- `syxb` → `permitted_gender_enum` BOTH/MALE/FEMALE
- `sx` → `sort_order`
- `category_code=PEIS`；套餐 `children_flag=true`，单项 `false`
- 套餐子项：`tcxms.xm.code` ↔ 已导入项目 `bus_no`
- 同套餐码多条（如 `20260417` 男/女）：`bus_no = code_性别`

## 执行顺序

1. 项目/明细：`V20260819c`（或已执行的等价导入）
2. 科室回填：`V20260819d`（可选）
3. 套餐：`V20260820a`
4. 包装单位：`V20260820c`（或等 health Flyway `V14`；否则体检收费报「包装单位未维护」）
5. 定价审计：`V20260820d`（应收缺 SALE 须在目录补零售价；`pricing_flag=false` 为不收费正常跳过）
6. 重新登录后在「体检目录」核对

## 注意

- 明细 `code`、套餐/项目 `bus_no` 均按幂等 upsert；重复执行安全
- 套餐依赖单项已在库（子项按 `bus_no` 关联）
