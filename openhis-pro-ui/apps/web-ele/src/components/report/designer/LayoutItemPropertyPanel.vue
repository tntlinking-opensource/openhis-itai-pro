<script setup lang="ts">
import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutBox, LayoutItem, LayoutPage } from '#/types/report-layout';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  boxFromDef,
  computeEffectiveBox,
  formatBoxSummary,
  hasBoxGeometry,
  isPositionedRenderType,
  OVERFLOW_OPTIONS,
  persistBoxFromEffective,
  readBoxFromDef,
  resolveBaseBox,
} from '#/utils/report/overlay-layout';
import {
  BOX_ALIGN_ACTIONS,
  alignLayoutBox,
  type BoxAlignMode,
} from '#/utils/report/overlay-box-align';

defineOptions({ name: 'LayoutItemPropertyPanel' });

const props = defineProps<{
  item: LayoutItem | null;
  elementDef?: PrintElementDefDto | null;
  /** 全部排版项，用于相对对齐 */
  items?: LayoutItem[];
  elements?: PrintElementDefDto[];
  page?: LayoutPage | null;
  layoutMode?: string;
  pipeline?: string;
  printOffsetMm?: number[];
  editable?: boolean;
}>();

const emit = defineEmits<{
  patch: [patch: Partial<LayoutItem>];
}>();

const isOverlay = computed(() => props.layoutMode === 'overlay');

const RENDER_TYPE_DEFAULT_FONT_PT: Record<string, number> = {
  staticTitle: 18,
  table: 9,
  text: 10,
  kvRow: 10,
};

const TEXT_ALIGN_OPTIONS = [
  { label: '居左', value: 'left' },
  { label: '居中', value: 'center' },
  { label: '居右', value: 'right' },
] as const;

function readDefStyleNumber(key: string): number | undefined {
  const value = props.elementDef?.defaultStyle?.[key];
  return typeof value === 'number' ? value : undefined;
}

function readDefStyleString(key: string): string | undefined {
  const value = props.elementDef?.defaultStyle?.[key];
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed || undefined;
}

/** 与后端 PrintElementRenderer 默认字号一致（未写 style.fontSizePt 时生效） */
const defaultFontSizePt = computed(() => {
  const fromDef = readDefStyleNumber('fontSizePt');
  if (fromDef != null) {
    return fromDef;
  }
  const renderType = props.elementDef?.renderType ?? '';
  return RENDER_TYPE_DEFAULT_FONT_PT[renderType] ?? 10;
});

const effectiveFontSizePt = computed(
  () => props.item?.style?.fontSizePt ?? defaultFontSizePt.value,
);

const defaultAlign = computed(() => readDefStyleString('align') ?? 'left');

const effectiveAlign = computed(() => props.item?.style?.align ?? defaultAlign.value);

const usesDefaultFontSize = computed(() => props.item?.style?.fontSizePt == null);

const usesDefaultAlign = computed(() => !props.item?.style?.align);

interface TableColumnRow {
  field: string;
  header: string;
  defAlign?: string;
}

const tableColumns = computed((): TableColumnRow[] => {
  if (props.elementDef?.renderType !== 'table') {
    return [];
  }
  const hidden = new Set(props.item?.hiddenColumns ?? []);
  const rows: TableColumnRow[] = [];
  for (const col of props.elementDef.columns ?? []) {
    const field = typeof col.field === 'string' ? col.field.trim() : '';
    if (!field || hidden.has(field)) {
      continue;
    }
    const header = typeof col.header === 'string' && col.header.trim() ? col.header.trim() : field;
    const defAlign =
      typeof col.align === 'string' && col.align.trim() ? col.align.trim() : undefined;
    rows.push({ field, header, defAlign });
  }
  return rows;
});

function effectiveColumnAlign(field: string, defAlign?: string): string {
  const override = props.item?.columnAligns?.[field];
  if (override) {
    return override;
  }
  if (defAlign) {
    return defAlign;
  }
  return effectiveAlign.value;
}

function usesDefaultColumnAlign(field: string, defAlign?: string): boolean {
  return !props.item?.columnAligns?.[field] && !defAlign;
}

function patchColumnAlign(field: string, align: string | undefined) {
  if (!props.item || !props.editable) {
    return;
  }
  const columnAligns = { ...(props.item.columnAligns ?? {}) };
  if (!align) {
    delete columnAligns[field];
  } else {
    columnAligns[field] = align;
  }
  emit('patch', {
    columnAligns: Object.keys(columnAligns).length > 0 ? columnAligns : undefined,
  });
}

const showBoxEditor = computed(
  () =>
    isOverlay.value ||
    (props.layoutMode === 'flow' &&
      props.pipeline === 'roll' &&
      isPositionedRenderType(props.elementDef?.renderType)),
);

const defaultBox = computed(() => readBoxFromDef(props.elementDef));

const baseBox = computed(() => (props.item ? resolveBaseBox(props.item, props.elementDef) : null));

const effectiveBox = computed(() =>
  props.item ? computeEffectiveBox(props.item, props.elementDef, props.printOffsetMm) : null,
);

const usesDefaultBox = computed(
  () => isOverlay.value && props.item != null && !props.item.box && !!defaultBox.value,
);

function currentBoxDraft(): NonNullable<LayoutItem['box']> {
  const base = baseBox.value;
  return {
    xMm: props.item?.box?.xMm ?? base?.xMm,
    yMm: props.item?.box?.yMm ?? base?.yMm,
    widthMm: props.item?.box?.widthMm ?? base?.widthMm,
    heightMm: props.item?.box?.heightMm ?? base?.heightMm,
    overflow: props.item?.box?.overflow ?? base?.overflow,
    rowHeightMm: props.item?.box?.rowHeightMm ?? base?.rowHeightMm,
  };
}

function num(field: keyof NonNullable<LayoutItem['box']>, value: number | undefined) {
  if (!props.item || !props.editable) {
    return;
  }
  const box = currentBoxDraft();
  box[field] = value;
  emit('patch', { box });
}

function patchBoxField(field: 'overflow' | 'rowHeightMm', value: string | number | undefined) {
  if (!props.item || !props.editable) {
    return;
  }
  const box = currentBoxDraft();
  if (field === 'overflow') {
    box.overflow = value ? String(value) : undefined;
  } else {
    box.rowHeightMm = typeof value === 'number' ? value : undefined;
  }
  emit('patch', { box });
}

function delta(field: 'dxMm' | 'dyMm', value: number | undefined) {
  if (!props.item || !props.editable) {
    return;
  }
  const boxDelta = { ...(props.item.boxDelta ?? { dxMm: 0, dyMm: 0 }) };
  boxDelta[field] = value ?? 0;
  emit('patch', { boxDelta });
}

function patchStyle(key: string, value: unknown) {
  if (!props.item || !props.editable) {
    return;
  }
  const style = { ...(props.item.style ?? {}) };
  (style as Record<string, unknown>)[key] = value;
  emit('patch', { style });
}

function applyDefaultBox() {
  if (!props.editable) {
    return;
  }
  const fromDef = boxFromDef(props.elementDef);
  if (!fromDef) {
    return;
  }
  emit('patch', { box: fromDef });
}

function clearCustomBox() {
  if (!props.editable) {
    return;
  }
  emit('patch', { box: undefined });
}

function resetBoxDelta() {
  if (!props.editable) {
    return;
  }
  emit('patch', { boxDelta: { dxMm: 0, dyMm: 0 } });
}

const elementMap = computed(() => {
  const map = new Map<string, PrintElementDefDto>();
  for (const el of props.elements ?? []) {
    if (el.elementCode) {
      map.set(el.elementCode, el);
    }
  }
  return map;
});

const alignReferenceOptions = computed(() => {
  const current = props.item?.elementCode;
  const out: Array<{ code: string; label: string }> = [];
  for (const row of props.items ?? []) {
    if (!row.elementCode || row.elementCode === current || row.visible === false) {
      continue;
    }
    const def = elementMap.value.get(row.elementCode);
    const effective = computeEffectiveBox(row, def, props.printOffsetMm);
    if (!effective || !hasBoxGeometry(effective)) {
      continue;
    }
    out.push({
      code: row.elementCode,
      label: def?.label ?? row.elementCode,
    });
  }
  return out;
});

const alignReferenceCode = ref('');

watch(
  () => [props.item?.elementCode, alignReferenceOptions.value.map((o) => o.code).join(',')],
  () => {
    const options = alignReferenceOptions.value;
    if (options.length === 0) {
      alignReferenceCode.value = '';
      return;
    }
    const preferred = options.find((o) => o.code === 'patientName') ?? options[0];
    if (!options.some((o) => o.code === alignReferenceCode.value)) {
      alignReferenceCode.value = preferred?.code ?? '';
    }
  },
  { immediate: true },
);

function applyBoxAlign(mode: BoxAlignMode) {
  if (!props.item || !props.editable || !alignReferenceCode.value) {
    return;
  }
  const refItem = props.items?.find((i) => i.elementCode === alignReferenceCode.value);
  if (!refItem) {
    return;
  }
  const refDef = elementMap.value.get(refItem.elementCode);
  const sourceEffective = computeEffectiveBox(props.item, props.elementDef, props.printOffsetMm);
  const refEffective = computeEffectiveBox(refItem, refDef, props.printOffsetMm);
  if (!sourceEffective || !refEffective) {
    return;
  }
  const alignedEffective = alignLayoutBox(sourceEffective, refEffective, mode);
  const box = persistBoxFromEffective(
    alignedEffective,
    props.item,
    props.elementDef,
    props.page ?? null,
    props.printOffsetMm,
  );
  emit('patch', { box });
}
</script>

<template>
  <div v-if="item" class="layout-item-props">
    <p class="layout-item-props__title">
      {{ elementDef?.label ?? item.elementCode }}
      <span class="layout-item-props__meta">{{ elementDef?.renderType }}</span>
    </p>
    <ElForm label-width="92px" size="small" :disabled="!editable">
      <ElFormItem label="标签覆盖">
        <ElInput
          :model-value="item.labelOverride ?? ''"
          placeholder="留空用默认"
          @update:model-value="(v: string) => emit('patch', { labelOverride: v || undefined })"
        />
      </ElFormItem>
      <ElFormItem label="字号 pt">
        <ElInputNumber
          :model-value="effectiveFontSizePt"
          :min="6"
          :max="36"
          :step="0.5"
          controls-position="right"
          class="layout-item-props__full-width"
          @update:model-value="(v: number | undefined) => patchStyle('fontSizePt', v)"
        />
        <p v-if="usesDefaultFontSize" class="layout-item-props__note">
          当前使用默认 {{ defaultFontSizePt }} pt（与 PDF 渲染一致）
        </p>
      </ElFormItem>
      <ElFormItem :label="elementDef?.renderType === 'table' ? '默认对齐' : '对齐'">
        <ElSelect
          :model-value="effectiveAlign"
          class="layout-item-props__full-width"
          @update:model-value="(v: string) => patchStyle('align', v || undefined)"
        >
          <ElOption
            v-for="opt in TEXT_ALIGN_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <p v-if="usesDefaultAlign" class="layout-item-props__note">
          当前使用默认：{{
            TEXT_ALIGN_OPTIONS.find((o) => o.value === defaultAlign)?.label ?? '居左'
          }}
        </p>
        <p v-if="elementDef?.renderType === 'table'" class="layout-item-props__hint">
          未单独设置对齐方式的列将使用此默认值。
        </p>
      </ElFormItem>

      <template v-if="elementDef?.renderType === 'table' && tableColumns.length">
        <div class="layout-item-props__section">列对齐</div>
        <ElFormItem v-for="col in tableColumns" :key="col.field" :label="col.header">
          <ElSelect
            :model-value="effectiveColumnAlign(col.field, col.defAlign)"
            class="layout-item-props__full-width"
            @update:model-value="(v: string) => patchColumnAlign(col.field, v || undefined)"
          >
            <ElOption
              v-for="opt in TEXT_ALIGN_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <p v-if="usesDefaultColumnAlign(col.field, col.defAlign)" class="layout-item-props__note">
            跟随默认对齐
          </p>
        </ElFormItem>
      </template>

      <ElFormItem label="加粗">
        <ElSwitch
          :model-value="item.style?.bold === true"
          @update:model-value="(v: boolean) => patchStyle('bold', v || undefined)"
        />
      </ElFormItem>
      <ElFormItem v-if="elementDef?.renderType === 'kvRow'" label="列数">
        <ElInputNumber
          :model-value="item.columns ?? 1"
          :min="1"
          :max="4"
          :step="1"
          controls-position="right"
          @update:model-value="
            (v: number | undefined) =>
              emit('patch', { columns: v != null && v > 1 ? v : undefined })
          "
        />
        <p class="layout-item-props__hint">
          键值行每行显示几个字段。58mm 热敏建议 1 列，80mm 建议 2 列，110mm 可设 2～3 列。
        </p>
      </ElFormItem>

      <template v-if="showBoxEditor">
        <div class="layout-item-props__section">纸面位置 (mm)</div>
        <p v-if="usesDefaultBox" class="layout-item-props__hint">
          当前跟随出厂默认位置；修改 X/Y/宽高后会保存为本模板的自定义坐标。
        </p>
        <ElFormItem v-if="defaultBox" label="出厂默认">
          <span class="layout-item-props__readonly">{{ formatBoxSummary(defaultBox) }}</span>
        </ElFormItem>
        <ElFormItem label="实际位置">
          <span class="layout-item-props__readonly">{{ formatBoxSummary(effectiveBox) }}</span>
        </ElFormItem>
        <ElFormItem label="X (mm)">
          <ElInputNumber
            :model-value="item.box?.xMm ?? baseBox?.xMm"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => num('xMm', v)"
          />
        </ElFormItem>
        <ElFormItem label="Y (mm)">
          <ElInputNumber
            :model-value="item.box?.yMm ?? baseBox?.yMm"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => num('yMm', v)"
          />
        </ElFormItem>
        <ElFormItem label="宽 (mm)">
          <ElInputNumber
            :model-value="item.box?.widthMm ?? baseBox?.widthMm"
            :min="1"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => num('widthMm', v)"
          />
        </ElFormItem>
        <ElFormItem label="高 (mm)">
          <ElInputNumber
            :model-value="item.box?.heightMm ?? baseBox?.heightMm"
            :min="1"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => num('heightMm', v)"
          />
        </ElFormItem>
        <ElFormItem label="溢出">
          <ElSelect
            :model-value="item.box?.overflow ?? baseBox?.overflow ?? 'clip'"
            clearable
            @update:model-value="(v: string) => patchBoxField('overflow', v)"
          >
            <ElOption
              v-for="opt in OVERFLOW_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="elementDef?.renderType === 'table'" label="行高 mm">
          <ElInputNumber
            :model-value="item.box?.rowHeightMm ?? baseBox?.rowHeightMm"
            :min="3"
            :step="0.5"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => patchBoxField('rowHeightMm', v)"
          />
          <p class="layout-item-props__hint">
            行高 × 框高 = 每页行数；超出自动分页，每页重复套打固定字段。
          </p>
        </ElFormItem>
        <template v-if="alignReferenceOptions.length">
          <div class="layout-item-props__section">相对对齐</div>
          <p class="layout-item-props__note layout-item-props__note--inline">
            以参考项落格为基准，调整当前项位置或尺寸（如姓名/年龄/性别顶对齐）。
          </p>
          <ElFormItem label="参考项">
            <ElSelect
              v-model="alignReferenceCode"
              class="layout-item-props__full-width"
              :disabled="!editable"
            >
              <ElOption
                v-for="opt in alignReferenceOptions"
                :key="opt.code"
                :label="opt.label"
                :value="opt.code"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="对齐">
            <div class="layout-item-props__btn-grid layout-item-props__btn-grid--4col">
              <ElButton
                v-for="action in BOX_ALIGN_ACTIONS"
                :key="action.mode"
                size="small"
                :title="action.title"
                :disabled="!editable"
                @click="applyBoxAlign(action.mode)"
              >
                {{ action.label }}
              </ElButton>
            </div>
          </ElFormItem>
        </template>
        <div class="layout-item-props__section">试打偏移 (mm)</div>
        <p class="layout-item-props__note layout-item-props__note--inline">
          仅用于试打时整体平移，不改上面保存的坐标；确认无误后再写入 X/Y。
        </p>
        <ElFormItem label="横向微调">
          <ElInputNumber
            :model-value="item.boxDelta?.dxMm ?? 0"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => delta('dxMm', v)"
          />
        </ElFormItem>
        <ElFormItem label="纵向微调">
          <ElInputNumber
            :model-value="item.boxDelta?.dyMm ?? 0"
            :step="0.1"
            :precision="1"
            controls-position="right"
            @update:model-value="(v: number | undefined) => delta('dyMm', v)"
          />
        </ElFormItem>
        <ElFormItem v-if="editable" label="快捷操作">
          <div class="layout-item-props__btn-grid layout-item-props__btn-grid--stack">
            <ElButton size="small" :disabled="!defaultBox" @click="applyDefaultBox">
              恢复出厂位置
            </ElButton>
            <ElButton size="small" @click="resetBoxDelta">清除试打偏移</ElButton>
            <ElButton size="small" :disabled="!item.box" @click="clearCustomBox">
              取消自定义坐标
            </ElButton>
          </div>
          <p class="layout-item-props__note">
            恢复出厂：写入平台推荐坐标；清除偏移：试打平移归零；取消自定义：改回跟随出厂默认。
          </p>
        </ElFormItem>
      </template>

      <ElFormItem v-if="elementDef?.bindPath" label="绑定路径">
        <span class="layout-item-props__readonly">{{ elementDef.bindPath }}</span>
      </ElFormItem>
    </ElForm>
  </div>
  <p v-else class="layout-item-props__empty">选中左侧排版项以编辑属性</p>
</template>

<style scoped>
.layout-item-props__title {
  margin: 0 0 12px;
  font-weight: 600;
  font-size: 14px;
}

.layout-item-props__section {
  margin: 12px 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.layout-item-props__meta {
  margin-left: 8px;
  font-weight: normal;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.layout-item-props__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.layout-item-props__hint {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-color-warning);
}

.layout-item-props__note {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.layout-item-props__note--inline {
  margin: 0 0 8px;
}

.layout-item-props__btn-grid {
  display: grid;
  gap: 6px;
  width: 100%;
}

.layout-item-props__btn-grid--4col {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.layout-item-props__btn-grid--stack {
  grid-template-columns: minmax(0, 1fr);
}

.layout-item-props__btn-grid :deep(.el-button) {
  margin: 0;
  width: 100%;
  padding-left: 6px;
  padding-right: 6px;
}

.layout-item-props__full-width {
  width: 100%;
}

.layout-item-props__empty {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
