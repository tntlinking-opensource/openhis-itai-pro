import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import type { InventoryItemDetail } from '#/api/core/common';

import { h } from 'vue';

import { ActiveFlagTag } from '@vben/common-ui';
import { setupVbenVxeTable, useVbenVxeGrid } from '@vben/plugins/vxe-table';

import { UnitQuantityInput } from '@vben-core/table-layout-ui';

import {
  dayjs,
  ElButton,
  ElDatePicker,
  ElImage,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  formatInventoryQtyUnitCellText,
  formatInventoryTotalAmountDisplay,
  formatInventoryUnitPriceDisplay,
} from '#/utils/inventory/inventory-qty-unit-display';
import { shouldInventoryRowShowPersistedForm } from '#/utils/inventory/inventory-doc-grid-edit';

import ProductSelectTable from '../components/select-product.vue';
import TraceCodeTooltip from '../components/trace/trace-code-tooltip.vue';
import SelectPriceAdjustmentItem from '../components/select-price-adjustment-item.vue';
import SelectPurchaseOrderMedicine from '../components/select-purchase-order-medicine.vue';
import SelectPurchaseReturnMedicine from '../components/select-purchase-return-medicine.vue';
import WarehouseProductSelectTable from '../components/select-warehouse-product.vue';
import { formatQuantityByUnit } from '../views/warehouse/util';
import { useVbenForm } from './form';

type TableCellValue = boolean | null | number | object | string | undefined;
interface TableRow {
  [key: string]: TableCellValue;
}

setupVbenVxeTable({
  configVxeTable: (vxeUI) => {
    vxeUI.setConfig({
      grid: {
        align: 'center',
        border: false,
        columnConfig: {
          resizable: true,
        },
        minHeight: 180,
        formConfig: {
          // 全局禁用vxe-table的表单配置，使用formOptions
          enabled: false,
        },
        proxyConfig: {
          autoLoad: true,
          response: {
            result: 'items',
            total: 'total',
            list: 'items',
          },
          showActiveMsg: true,
          showResponseMsg: false,
        },
        round: true,
        showOverflow: true,
        size: 'small',
      } as VxeTableGridOptions,
    });

    // 表格配置项可以用 cellRender: { name: 'CellImage' },
    vxeUI.renderer.add('CellImage', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { column, row } = params;
        const src = row[column.field];
        return h(ElImage, { src, previewSrcList: [src], ...props });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellLink' },
    vxeUI.renderer.add('CellLink', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(ElButton, { size: 'small', link: true }, { default: () => props?.text });
      },
    });

    vxeUI.renderer.add('CellButton', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(ElButton, { link: true, ...props }, { default: () => props?.label });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellActiveFlag' },
    vxeUI.renderer.add('CellActiveFlag', {
      renderTableDefault(_renderOpts, params) {
        const { row } = params;
        return h(ActiveFlagTag, {
          activeFlag: row.activeFlag,
          enumText: row.activeFlag_enumText,
        });
      },
    });

    vxeUI.renderer.add('ProductSelectTable', {
      autofocus: '.vxe-input--inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const onSelected = props?.onSelected as
          | ((product: InventoryItemDetail, row: TableRow, column: { field: string }) => void)
          | undefined;

        return h(ProductSelectTable, {
          ...props,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: InventoryItemDetail) => {
            const next: TableCellValue = val;
            row[column.field] = next;
            if (onSelected) {
              onSelected(val, row, column);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        return h('span', row[column.field]?.productName ?? row[column.field] ?? '');
      },
    });
    /** 药库订货单：medication-info 下拉选品（与采购入库 ProductSelectTable 同模式） */
    const purchaseOrderMedicineSelectRenderer = {
      autofocus: '.vxe-input--inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const onSelected = props?.onSelected as
          | ((product: TableRow, row: TableRow) => void)
          | undefined;

        return h(SelectPurchaseOrderMedicine, {
          ...props,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: TableCellValue) => {
            row[column.field] = val;
            if (val && onSelected) {
              onSelected(val as TableRow, row);
            }
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return purchaseOrderMedicineSelectRenderer.renderTableEdit(renderOpts, { row, column });
        }
        const v = row[column.field] as undefined | { name?: string };
        return h('span', v?.name ?? row.itemName ?? '');
      },
    };
    vxeUI.renderer.add('PurchaseOrderMedicineSelect', purchaseOrderMedicineSelectRenderer);

    /** 调价单：可选项目下拉选品（同构药库进货单 PurchaseOrderMedicineSelect） */
    vxeUI.renderer.add('PriceAdjustmentItemSelect', {
      autofocus: '.vxe-input--inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const onSelected = props?.onSelected as
          | ((item: TableRow, row: TableRow) => void)
          | undefined;

        return h(SelectPriceAdjustmentItem, {
          ...props,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: TableCellValue) => {
            row[column.field] = val;
            if (val && onSelected) {
              onSelected(val as TableRow, row);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        const v = row[column.field] as undefined | { name?: string };
        return h('span', v?.name ?? row.itemName ?? '');
      },
    });

    /** 药库采购退货：medication-info 下拉选品（与订货 PurchaseOrderMedicineSelect 同模式） */
    vxeUI.renderer.add('PurchaseReturnMedicineSelect', {
      autofocus: '.vxe-input--inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const onSelected = props?.onSelected as
          | ((product: TableRow, row: TableRow) => void)
          | undefined;

        return h(SelectPurchaseReturnMedicine, {
          ...props,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: TableCellValue) => {
            row[column.field] = val;
            if (val && onSelected) {
              onSelected(val as TableRow, row);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        const v = row[column.field] as undefined | { name?: string };
        return h('span', v?.name ?? row.itemName ?? '');
      },
    });

    vxeUI.renderer.add('WarehouseProductSelectTable', {
      autofocus: '.vxe-input--inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const onSelected = props?.onSelected as
          | ((product: InventoryItemDetail, row: TableRow, column: { field: string }) => void)
          | undefined;

        return h(WarehouseProductSelectTable, {
          ...props,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: InventoryItemDetail) => {
            const next: TableCellValue = val;
            row[column.field] = next;
            if (onSelected) {
              onSelected(val, row, column);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        return h('span', row[column.field]?.name ?? row[column.field] ?? '');
      },
    });

    // 表格配置：editRender: { name: 'UnitQuantityInput', props: { partPercent, unitLabel, minUnitLabel, isUnit } }
    vxeUI.renderer.add('UnitQuantityInput', {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;

        const partPercent = row.partPercent ?? props?.partPercent ?? 1;
        const unitLabel = row.unitCode_dictText ?? row?.productInfo?.unitCodeDictText ?? '';
        const minUnitLabel =
          row.minUnitCode_dictText ?? row?.productInfo?.minUnitCodeDictText ?? '';
        const isUnit = row.isUnit ?? props?.isUnit ?? false;
        const onChange = props?.onChange as ((val: number, row: TableRow) => void) | undefined;
        return h(UnitQuantityInput, {
          ...props,
          partPercent,
          unitLabel,
          minUnitLabel,
          isUnit,
          modelValue: row[column.field],
          'onUpdate:modelValue': (val: number) => {
            row[column.field] = val;
            if (onChange) {
              onChange(val, row);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        const total = Number(row[column.field] ?? 0);
        const partPercent = Number(row.partPercent ?? 1) || 1;
        const unitLabel = row.unitCode_dictText ?? row?.productInfo?.unitCodeDictText ?? '';
        const minUnitLabel =
          row.minUnitCode_dictText ?? row?.productInfo?.minUnitCodeDictText ?? '';
        const isUnit = !!row.isUnit;

        const text = formatQuantityByUnit(total, partPercent, unitLabel, minUnitLabel, isUnit);
        return h('span', text);
      },
    });

    const elDatePickerRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const fieldValue = row[column.field];
        const valueFormat = (props?.valueFormat as string | undefined) || 'YYYY-MM-DD HH:mm:ss';
        const displayFormat = (props?.format as string | undefined) || valueFormat;
        const pickerType = (props?.type as string | undefined) ?? 'datetime';

        const formatStored = (val: Date | string) =>
          dayjs(val).isValid() ? dayjs(val).format(valueFormat) : String(val);

        if (pickerType === 'daterange') {
          let rangeVal: [string, string] | undefined;
          if (Array.isArray(fieldValue) && fieldValue.length === 2) {
            rangeVal = [String(fieldValue[0]), String(fieldValue[1])];
          } else if (typeof fieldValue === 'string' && fieldValue.trim() !== '') {
            let sep = '';
            if (fieldValue.includes('~')) sep = '~';
            else if (fieldValue.includes('至')) sep = '至';
            if (sep) {
              const parts = fieldValue.split(sep).map((s) => s.trim());
              if (parts.length === 2 && parts[0] && parts[1]) {
                rangeVal = [parts[0], parts[1]];
              }
            }
          }
          return h(ElDatePicker, {
            ...props,
            type: 'daterange',
            valueFormat,
            format: displayFormat,
            class: (props as Record<string, unknown>)?.class ?? '!w-full',
            modelValue: rangeVal,
            'onUpdate:modelValue': (val: null | string[] | undefined) => {
              if (val === null || val === undefined || (Array.isArray(val) && val.length === 0)) {
                row[column.field] = undefined;
              } else if (Array.isArray(val) && val.length === 2) {
                row[column.field] = [String(val[0]), String(val[1])];
              }
            },
          });
        }

        const datePickerProps = {
          ...props,
          type: pickerType,
          valueFormat,
          format: displayFormat,
        };

        return h(ElDatePicker, {
          ...datePickerProps,
          modelValue: fieldValue
            ? dayjs(fieldValue as string).isValid()
              ? dayjs(fieldValue as string).format(valueFormat)
              : fieldValue
            : undefined,
          'onUpdate:modelValue': (val: Date | null | string | undefined) => {
            if (val === null || val === undefined || val === '') {
              row[column.field] = undefined;
            } else if (val instanceof Date) {
              row[column.field] = formatStored(val);
            } else if (typeof val === 'string') {
              row[column.field] = dayjs(val).isValid() ? formatStored(val) : val;
            } else {
              row[column.field] = val;
            }
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return elDatePickerRenderer.renderTableEdit(renderOpts, { row, column });
        }
        const value = row[column.field];
        const pickerType = (renderOpts.props?.type as string | undefined) ?? 'datetime';
        if (pickerType === 'daterange') {
          if (!value) return h('span', '');
          if (Array.isArray(value) && value.length === 2) {
            return h('span', `${value[0]} ~ ${value[1]}`);
          }
          return h('span', String(value));
        }
        if (!value) return h('span', '');
        const vf = (renderOpts.props?.valueFormat as string | undefined) || 'YYYY-MM-DD HH:mm:ss';
        const formatted = dayjs(value as string).isValid()
          ? dayjs(value as string).format(vf)
          : value;
        return h('span', String(formatted));
      },
    };
    vxeUI.renderer.add('ElDatePicker', elDatePickerRenderer);

    /** 未完成行常驻：ElInput 文本列（批号/备注等） */
    const inventoryPersistElInputRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const maxlength = props?.maxlength as number | undefined;
        const clearable = props?.clearable === true;
        return h(ElInput, {
          class: '!w-full',
          clearable,
          maxlength,
          modelValue: String(row[column.field] ?? ''),
          'onUpdate:modelValue': (val: string) => {
            row[column.field] = val;
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return inventoryPersistElInputRenderer.renderTableEdit(renderOpts, { row, column });
        }
        return h('span', String(row[column.field] ?? ''));
      },
    };
    vxeUI.renderer.add('InventoryPersistElInput', inventoryPersistElInputRenderer);

    /** 追溯码：未完成行常驻 ElInput；只读回显 TraceCodeTooltip */
    const inventoryTraceNoCellRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const maxlength = props?.maxlength as number | undefined;
        const clearable = props?.clearable === true;
        return h(ElInput, {
          class: '!w-full',
          clearable,
          maxlength,
          modelValue: String(row[column.field] ?? ''),
          'onUpdate:modelValue': (val: string) => {
            row[column.field] = val;
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return inventoryTraceNoCellRenderer.renderTableEdit(renderOpts, { row, column });
        }
        return h(TraceCodeTooltip, { value: String(row[column.field] ?? '') });
      },
    };
    vxeUI.renderer.add('InventoryTraceNoCell', inventoryTraceNoCellRenderer);

    /** 只读追溯码列（TraceCodeTooltip） */
    vxeUI.renderer.add('TraceCodeTooltipCell', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { row, column } = params;
        const field = String(props?.field ?? column.field ?? 'traceNo');
        return h(TraceCodeTooltip, { value: String(row[field] ?? '') });
      },
    });

    /** 未完成行常驻：ElInputNumber 数字列（数量/损益数量等） */
    const inventoryPersistElInputNumberRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const raw = Number(row[column.field]);
        const model = Number.isFinite(raw) ? raw : undefined;
        return h(ElInputNumber, {
          align: (props?.align as string | undefined) ?? 'center',
          class: '!w-full',
          controls: props?.controls !== false,
          controlsPosition: props?.controlsPosition as 'right' | undefined,
          min: (props?.min as number | undefined) ?? 0,
          precision: props?.precision as number | undefined,
          modelValue: model,
          'onUpdate:modelValue': (val: number | undefined) => {
            row[column.field] = val === undefined || val === null ? undefined : val;
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return inventoryPersistElInputNumberRenderer.renderTableEdit(renderOpts, { row, column });
        }
        const val = row[column.field];
        return h('span', val === undefined || val === null || val === '' ? '' : String(val));
      },
    };
    vxeUI.renderer.add('InventoryPersistElInputNumber', inventoryPersistElInputNumberRenderer);

    /** 未完成行常驻：ElSelect 下拉列（变更原因等） */
    const inventoryPersistElSelectRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props, options } = renderOpts;
        type RowOpt = { label: string; value: string };
        const opts = (options ?? props?.options ?? []) as RowOpt[];
        return h(
          ElSelect,
          {
            class: 'w-full',
            filterable: props?.filterable === true,
            clearable: props?.clearable === true,
            placeholder: props?.placeholder as string | undefined,
            modelValue: row[column.field],
            'onUpdate:modelValue': (val: string) => {
              row[column.field] = val;
            },
          },
          () =>
            opts.map((opt) =>
              h(ElOption, { key: String(opt.value), label: opt.label, value: opt.value }),
            ),
        );
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return inventoryPersistElSelectRenderer.renderTableEdit(renderOpts, { row, column });
        }
        const { options, props } = renderOpts;
        type RowOpt = { label: string; value: string };
        const opts = (options ?? props?.options ?? []) as RowOpt[];
        const val = String(row[column.field] ?? '');
        const label = opts.find((o) => String(o.value) === val)?.label ?? val;
        return h('span', label);
      },
    };
    vxeUI.renderer.add('InventoryPersistElSelect', inventoryPersistElSelectRenderer);

    /**
     * 数量 + 单位合并单元格（药库进货/出库/药房入库等）。
     * props: qtyField, unitField, useBandSelect（默认 true=UnitSelect），onUnitChange
     */
    const inventoryQtyUnitCellRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const qtyField = String(props?.qtyField ?? 'itemQuantity');
        const unitField = String(props?.unitField ?? 'selectedBandKind');
        const useBandSelect = props?.useBandSelect !== false;
        const onUnitChange = props?.onUnitChange as ((row: TableRow) => void) | undefined;
        const onQtyChange = props?.onQtyChange as ((row: TableRow) => void) | undefined;
        const allowNegative = props?.allowNegative === true;
        const qty = Number(row[qtyField]);
        const qtyModel = Number.isFinite(qty) ? qty : undefined;

        const qtyInput = h(ElInputNumber, {
          controls: false,
          align: 'center',
          class: '!min-w-0 flex-1',
          ...(allowNegative ? {} : { min: 0 }),
          modelValue: qtyModel,
          'onUpdate:modelValue': (val: number | undefined) => {
            row[qtyField] = val === undefined || val === null ? undefined : val;
            onQtyChange?.(row);
          },
        });

        let unitInput;
        if (useBandSelect) {
          type UnitOpt = { label?: string; value: string; value_dictText?: string };
          const rawList = (row.unitList as UnitOpt[]) || [];
          const options = rawList.map((u) => ({
            label: String(u.label ?? u.value_dictText ?? u.value ?? ''),
            value: u.value,
          }));
          unitInput = h(
            'div',
            {
              class: '!min-w-0 flex-1',
              onMousedown: (e: MouseEvent) => e.stopPropagation(),
              onClick: (e: MouseEvent) => e.stopPropagation(),
            },
            [
              h(ElSelect, {
                class: 'w-full',
                modelValue: row[unitField],
                options,
                'onUpdate:modelValue': (val: string) => {
                  row[unitField] = val;
                  onUnitChange?.(row);
                },
              }),
            ],
          );
        } else {
          unitInput = h(ElInput, {
            class: '!min-w-0 flex-1',
            maxlength: 32,
            clearable: true,
            modelValue: String(row[unitField] ?? ''),
            'onUpdate:modelValue': (val: string) => {
              row[unitField] = val;
              onUnitChange?.(row);
            },
          });
        }

        return h('div', { class: 'flex w-full items-center justify-center gap-1' }, [
          qtyInput,
          unitInput,
        ]);
      },
      renderTableCell(renderOpts, params) {
        if (shouldInventoryRowShowPersistedForm(params.row)) {
          return inventoryQtyUnitCellRenderer.renderTableEdit(renderOpts, params);
        }
        const { row } = params;
        const { props } = renderOpts;
        const qtyField = String(props?.qtyField ?? 'itemQuantity');
        const unitField = String(props?.unitField ?? 'selectedBandKind');
        const text = formatInventoryQtyUnitCellText(row, qtyField, unitField);
        return h('span', { class: 'block w-full text-center' }, text);
      },
    };
    vxeUI.renderer.add('InventoryQtyUnitCell', inventoryQtyUnitCellRenderer);

    /** 只读：数量 + 单位合并展示（盘点当前库存等，常驻表单行也保持只读） */
    vxeUI.renderer.add('InventoryQtyUnitReadonlyCell', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { row } = params;
        const qtyField = String(props?.qtyField ?? 'itemQuantity');
        const unitField = String(props?.unitField ?? 'selectedBandKind');
        const text = formatInventoryQtyUnitCellText(row, qtyField, unitField);
        return h('span', { class: 'block w-full text-center' }, text);
      },
    });

    /** 单价：编辑为数字；回显两位小数 + 计量单位 */
    const inventoryUnitPriceCellRenderer = {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        const priceField = String(props?.priceField ?? column.field ?? 'unitPrice');
        const price = Number(row[priceField]);
        const priceModel = Number.isFinite(price) ? price : undefined;
        return h(ElInputNumber, {
          controls: false,
          align: 'right',
          class: '!w-full',
          min: 0,
          precision: 2,
          modelValue: priceModel,
          'onUpdate:modelValue': (val: number | undefined) => {
            row[priceField] = val === undefined || val === null ? 0 : val;
          },
        });
      },
      renderTableCell(renderOpts, { row, column }) {
        if (shouldInventoryRowShowPersistedForm(row)) {
          return inventoryUnitPriceCellRenderer.renderTableEdit(renderOpts, { row, column });
        }
        const { props } = renderOpts;
        const priceField = String(props?.priceField ?? column.field ?? 'unitPrice');
        const unitField = String(props?.unitField ?? 'selectedBandKind');
        const text = formatInventoryUnitPriceDisplay(row, priceField, unitField);
        return h('span', { class: 'block w-full text-right' }, text);
      },
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { row, column } = params;
        const priceField = String(props?.priceField ?? column.field ?? 'unitPrice');
        const unitField = String(props?.unitField ?? 'selectedBandKind');
        const text = formatInventoryUnitPriceDisplay(row, priceField, unitField);
        return h('span', { class: 'block w-full text-right' }, text);
      },
    };
    vxeUI.renderer.add('InventoryUnitPriceCell', inventoryUnitPriceCellRenderer);

    /** 总金额：只读回显两位小数 + `元` */
    vxeUI.renderer.add('InventoryTotalAmountCell', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { row, column } = params;
        const amountField = String(props?.amountField ?? column.field ?? 'totalAmount');
        const text = formatInventoryTotalAmountDisplay(row, amountField);
        return h('span', { class: 'block w-full text-right' }, text);
      },
    });

    /** 行内 options 字段驱动的 ElSelect（如护士站皮试执行人/核对人） */
    vxeUI.renderer.add('RowOptionsElSelect', {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        type RowOpt = { label: string; value: string };
        const optionsField = String(props?.optionsField ?? '');
        const options = (row[optionsField] as RowOpt[] | undefined) ?? [];
        return h(
          ElSelect,
          {
            class: 'w-full',
            filterable: props?.filterable === true,
            clearable: props?.clearable === true,
            placeholder: props?.placeholder as string | undefined,
            modelValue: row[column.field],
            'onUpdate:modelValue': (val: string) => {
              row[column.field] = val;
            },
          },
          () =>
            options.map((opt) =>
              h(ElOption, { key: opt.value, label: opt.label, value: opt.value }),
            ),
        );
      },
      renderTableCell(renderOpts, { row, column }) {
        type RowOpt = { label: string; value: string };
        const optionsField = String(renderOpts.props?.optionsField ?? '');
        const options = (row[optionsField] as RowOpt[] | undefined) ?? [];
        const value = String(row[column.field] ?? '');
        const hit = options.find((item) => item.value === value);
        return h('span', hit?.label || value || '—');
      },
    });

    // 自定义单位选择器，支持根据每行的 unitList 动态设置选项
    vxeUI.renderer.add('UnitSelect', {
      autofocus: '.el-input__inner',
      renderTableEdit(renderOpts, { row, column }) {
        const { props } = renderOpts;
        type UnitOpt = { label?: string; value: string; value_dictText?: string };
        const rawList = (row.unitList as UnitOpt[]) || [];
        const options = rawList.map((u) => ({
          label: String(u.label ?? u.value_dictText ?? u.value ?? ''),
          value: u.value,
        }));
        const onChange = props?.onChange as ((val: string, row: TableRow) => void) | undefined;

        return h(ElSelect, {
          ...props,
          modelValue: row[column.field],
          options,
          'onUpdate:modelValue': (val: string) => {
            row[column.field] = val;
            if (onChange) {
              onChange(val, row);
            }
          },
        });
      },
      renderTableCell(_, { row, column }) {
        const value = row[column.field];
        type UnitOpt = { label?: string; value: string; value_dictText?: string };
        const unitList = (row.unitList as UnitOpt[]) || [];
        const option = unitList.find((item) => item.value === value);
        const text = option?.label ?? option?.value_dictText ?? value ?? '';
        return h('span', text || '');
      },
    });

    /** 采购退货明细：追溯码扫码入口 */
    vxeUI.renderer.add('PurchaseReturnScanCell', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { row } = params;
        const onScan = props?.onScan as ((r: TableRow) => void) | undefined;
        return h(
          ElButton,
          {
            type: 'primary',
            link: true,
            icon: 'Edit',
            onClick: () => onScan?.(row),
          },
          () => '扫码',
        );
      },
    });

    // 这里可以自行扩展 vxe-table 的全局配置，比如自定义格式化
  },
  useVbenForm,
});

export { useVbenVxeGrid };

export type * from '@vben/plugins/vxe-table';
