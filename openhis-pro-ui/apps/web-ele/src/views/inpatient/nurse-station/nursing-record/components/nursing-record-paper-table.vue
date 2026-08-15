<script setup lang="ts">
import type { NursingRecordPaperHeader, NursingRecordViewRow } from './nursing-record.types';

import { Delete } from '@element-plus/icons-vue';

import { nextTick, onMounted, watch } from 'vue';

import { usePaperSheetFullscreen } from '../../composables/use-paper-sheet-fullscreen';
import {
  composeNursingRecordDatetime,
  getNowDateTime,
  splitNursingRecordDatetime,
} from '../services/map-nurse-station-nursing-record';

defineOptions({
  name: 'NursingRecordPaperTable',
});

interface NurseSignatureOption {
  label: string;
  value: string;
}

interface Props {
  paperHeader?: NursingRecordPaperHeader | null;
  rows: NursingRecordViewRow[];
  nurseOptions?: NurseSignatureOption[];
  defaultNurseSignatureLabel?: string;
  savingAll?: boolean;
  unsavedCount?: number;
  weekRangeLabel?: string;
  /** 只读：隐藏写操作并禁用单元格编辑 */
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  paperHeader: null,
  nurseOptions: () => [],
  defaultNurseSignatureLabel: '',
  savingAll: false,
  unsavedCount: 0,
  weekRangeLabel: '',
  readonly: false,
});

const emit = defineEmits<{
  addRow: [];
  deleteRow: [id: string];
  markDirty: [id: string];
  refresh: [];
  saveAll: [];
}>();

const { isFullscreen, toggleFullscreen } = usePaperSheetFullscreen();

/** 同步日期时间选择器到行字段（支持面板选择与手动输入）。 */
function syncRowDatetime(row: NursingRecordViewRow, value: null | string | undefined) {
  if (props.readonly) {
    return;
  }
  const parts = splitNursingRecordDatetime(value);
  row.recordDate = parts.recordDate;
  row.recordTime = parts.recordTime;
  emit('markDirty', row.id);
}

/** 行日期时间绑定值（空则 undefined，避免占位文案）。 */
function datetimeModelValue(row: NursingRecordViewRow) {
  return composeNursingRecordDatetime(row.recordDate, row.recordTime) || undefined;
}

/**
 * 选中/聚焦行时带入当前时间与登录人签名（仅补空）。
 * 不标记 dirty：仅点选空行不应进入「未保存」，真正改字段时再 dirty。
 */
function ensureRowDefaults(row: NursingRecordViewRow) {
  if (props.readonly) {
    return;
  }
  const now = getNowDateTime();
  if (!row.recordTime?.trim()) {
    row.recordTime = now.time;
  }
  if (!row.recordDate?.trim()) {
    row.recordDate = now.date;
  }
  if (!row.nurseSignature?.trim() && props.defaultNurseSignatureLabel) {
    row.nurseSignature = props.defaultNurseSignatureLabel;
  }
}

/** 字段变更时标记未保存。 */
function handleFieldDirty(row: NursingRecordViewRow) {
  if (props.readonly) {
    return;
  }
  emit('markDirty', row.id);
}

const OBSERVATION_TEXTAREA_MIN_HEIGHT_PX = 29;

/** 按内容撑开病情观察 textarea 高度。 */
function resizeObservationTextarea(el: HTMLTextAreaElement | null) {
  if (!el) {
    return;
  }
  el.style.height = 'auto';
  el.style.height = `${Math.max(OBSERVATION_TEXTAREA_MIN_HEIGHT_PX, el.scrollHeight)}px`;
}

/** 同步表格内全部病情观察 textarea 高度（加载/回显后）。 */
function syncAllObservationTextareaHeights() {
  document
    .querySelectorAll<HTMLTextAreaElement>('.paper-record__table .paper-field--textarea')
    .forEach((el) => resizeObservationTextarea(el));
}

/** 病情观察输入：自动增高并标记未保存。 */
function handleObservationInput(row: NursingRecordViewRow, event: Event) {
  resizeObservationTextarea(event.target as HTMLTextAreaElement);
  if (props.readonly) {
    return;
  }
  handleFieldDirty(row);
}

watch(
  () => props.rows,
  () => {
    void nextTick(() => {
      syncAllObservationTextareaHeights();
    });
  },
  { flush: 'post' },
);

onMounted(() => {
  void nextTick(() => {
    syncAllObservationTextareaHeights();
  });
});
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
    <div class="paper-record" :class="{ 'is-fullscreen': isFullscreen }">
      <div class="paper-record__toolbar">
        <div class="paper-record__toolbar-main">
          <slot name="toolbar-main" />
          <span v-if="weekRangeLabel" class="paper-record__week">{{ weekRangeLabel }}</span>
          <span v-if="!readonly && unsavedCount > 0" class="paper-record__unsaved"
            >未保存 {{ unsavedCount }} 条</span
          >
        </div>
        <div class="paper-record__toolbar-actions">
          <slot name="toolbar-extra" />
          <ElButton plain @click="emit('refresh')">刷新</ElButton>
          <ElButton plain @click="toggleFullscreen">
            {{ isFullscreen ? '退出全屏' : '全屏' }}
          </ElButton>
          <ElButton v-if="!readonly" type="primary" plain @click="emit('addRow')">新增行</ElButton>
          <ElButton v-if="!readonly" :loading="savingAll" type="primary" @click="emit('saveAll')">
            保存
          </ElButton>
        </div>
      </div>

      <div class="paper-record__viewport is-fluid">
        <div class="paper-record__stage">
          <div class="paper-record__shell">
            <div class="paper-record__sheet is-fluid">
              <div class="paper-record__doc-head">
                <header class="sheet-title">护 理 记 录 单</header>

                <div v-if="paperHeader" class="patient-info-line">
                  <span>姓名：{{ paperHeader.name || '' }}</span>
                  <span>性别：{{ paperHeader.gender || '' }}</span>
                  <span>年龄：{{ paperHeader.age ?? '' }}</span>
                  <span>病室：{{ paperHeader.wardName || '' }}</span>
                  <span>床号：{{ paperHeader.bedNo || '' }}</span>
                  <span>病历号：{{ paperHeader.inpatientNo || '' }}</span>
                  <span>入院日期：{{ paperHeader.admitAt || '' }}</span>
                  <span>出院日期：{{ paperHeader.dischargeAt || '' }}</span>
                </div>
              </div>

              <table class="paper-record__table">
                <colgroup>
                  <col class="col-datetime" />
                  <col class="col-vital" span="5" />
                  <col class="col-consciousness" />
                  <col class="col-pupil" span="2" />
                  <col class="col-io-name" />
                  <col class="col-io-amount" />
                  <col class="col-io-name" />
                  <col class="col-io-amount" />
                  <col class="col-io-color" />
                  <col class="col-observation" />
                  <col class="col-signature" />
                </colgroup>
                <thead>
                  <tr>
                    <th class="col-datetime" rowspan="2">日期<br />时间</th>
                    <th class="col-vital">体温</th>
                    <th class="col-vital">脉搏</th>
                    <th class="col-vital">心率</th>
                    <th class="col-vital">呼吸</th>
                    <th class="col-vital">血压</th>
                    <th class="col-consciousness" rowspan="2">意识</th>
                    <th class="col-pupil" colspan="2">瞳孔</th>
                    <th class="col-io" colspan="2">入量</th>
                    <th class="col-io" colspan="3">出量</th>
                    <th class="col-observation" rowspan="2">病情观察及护理</th>
                    <th class="col-signature" rowspan="2">护士签名</th>
                  </tr>
                  <tr>
                    <th class="col-vital">℃</th>
                    <th class="col-vital">次/分</th>
                    <th class="col-vital">次/分</th>
                    <th class="col-vital">次/分</th>
                    <th class="col-vital">mmHg</th>
                    <th class="col-pupil">左</th>
                    <th class="col-pupil">右</th>
                    <th class="col-io-name">名称</th>
                    <th class="col-io-amount">ml</th>
                    <th class="col-io-name">名称</th>
                    <th class="col-io-amount">ml</th>
                    <th class="col-io-color">颜色</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in rows"
                    :key="row.id"
                    class="paper-record__row"
                    @focusin="ensureRowDefaults(row)"
                  >
                    <td class="col-datetime">
                      <ElDatePicker
                        class="paper-datetime-picker"
                        type="datetime"
                        format="MM-DD HH:mm"
                        value-format="YYYY-MM-DD HH:mm"
                        time-format="HH:mm"
                        placeholder=" "
                        :editable="!readonly"
                        :disabled="readonly"
                        :clearable="false"
                        :model-value="datetimeModelValue(row)"
                        data-nav-col="0"
                        @update:model-value="syncRowDatetime(row, $event as string | null)"
                      />
                      <button
                        v-if="!readonly && row.rowType === 'extra'"
                        type="button"
                        class="paper-row-delete"
                        title="删除此行"
                        @click="emit('deleteRow', row.id)"
                      >
                        <ElIcon :size="10"><Delete /></ElIcon>
                      </button>
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.temperature"
                        class="paper-field"
                        type="text"
                        data-nav-col="1"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.pulse"
                        class="paper-field"
                        type="text"
                        data-nav-col="2"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.heartRate"
                        class="paper-field"
                        type="text"
                        data-nav-col="3"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.respiratory"
                        class="paper-field"
                        type="text"
                        data-nav-col="4"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.bloodPressure"
                        class="paper-field"
                        type="text"
                        data-nav-col="5"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-consciousness">
                      <input
                        v-model="row.consciousness"
                        class="paper-field"
                        type="text"
                        data-nav-col="6"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-pupil">
                      <input
                        v-model="row.pupilLeft"
                        class="paper-field"
                        type="text"
                        data-nav-col="7"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-pupil">
                      <input
                        v-model="row.pupilRight"
                        class="paper-field"
                        type="text"
                        data-nav-col="8"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-name">
                      <input
                        v-model="row.intakeName"
                        class="paper-field"
                        type="text"
                        data-nav-col="9"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-amount">
                      <input
                        v-model="row.intakeAmount"
                        class="paper-field"
                        type="text"
                        data-nav-col="10"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-name">
                      <input
                        v-model="row.outputName"
                        class="paper-field"
                        type="text"
                        data-nav-col="11"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-amount">
                      <input
                        v-model="row.outputAmount"
                        class="paper-field"
                        type="text"
                        data-nav-col="12"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-color">
                      <input
                        v-model="row.outputColorCharacter"
                        class="paper-field"
                        type="text"
                        data-nav-col="13"
                        :readonly="readonly"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-observation">
                      <textarea
                        v-model="row.conditionMeasure"
                        class="paper-field paper-field--textarea"
                        rows="1"
                        data-nav-col="14"
                        :readonly="readonly"
                        @input="handleObservationInput(row, $event)"
                      />
                    </td>
                    <td class="col-signature">
                      <ElSelect
                        v-model="row.nurseSignature"
                        class="paper-signature-select"
                        filterable
                        clearable
                        placeholder=" "
                        data-nav-col="15"
                        :disabled="readonly"
                        @change="handleFieldDirty(row)"
                      >
                        <ElOption
                          v-for="opt in nurseOptions"
                          :key="opt.value"
                          :label="opt.label"
                          :value="opt.value"
                        />
                      </ElSelect>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.paper-record {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.paper-record.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  max-width: 100vw;
  max-height: 100vh;
  background: #eef0f3;
}

.paper-record__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: 0 0 auto;
  padding: 8px 12px 6px;
  background: #f3f5f8;
}

.paper-record.is-fullscreen .paper-record__toolbar {
  background: #f3f5f8;
}

.paper-record__toolbar-main {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  color: #020817;
}

.paper-record__week {
  color: #020817;
}

.paper-record__unsaved {
  color: #020817;
}

.paper-record__toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}

.paper-record__toolbar-actions :deep(.el-segmented) {
  --el-border-radius-base: 6px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  padding: 2px;
}

.paper-record__viewport {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.paper-record__viewport.is-fluid {
  overflow: auto;
  background: #fff;
}

.paper-record.is-fullscreen .paper-record__viewport.is-fluid {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.paper-record__stage {
  width: fit-content;
  margin: 0 auto;
}

.paper-record.is-fullscreen .paper-record__stage {
  width: 100%;
  margin: 0;
}

.paper-record__shell {
  position: relative;
  overflow: hidden;
}

.paper-record__viewport.is-fluid .paper-record__shell {
  overflow: visible;
}

.paper-record__sheet {
  box-sizing: border-box;
  background: #fff;
  color: #020817;
}

.paper-record__sheet.is-fluid {
  width: 100%;
  min-height: 100%;
  padding: 12px;
}

/* 单据抬头 + 表格 thead：与体温单 head-label / sheet-head-grid 同字体（非宋体） */
.paper-record__doc-head {
  color: #020817;
  font-family: inherit;
  margin-bottom: 6px;
}

.sheet-title {
  text-align: center;
  font-family: inherit;
  font-size: 28px;
  letter-spacing: 12px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0 0 6px;
  color: #020817;
}

.patient-info-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  min-height: 24px;
  padding: 2px 0;
  font-family: inherit;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  color: #020817;
}

.patient-info-line span {
  color: #020817;
  font-size: inherit;
  font-weight: inherit;
  line-height: inherit;
}

.paper-record__table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  border: 1px solid #222;
  color: #020817;
}

.paper-record__table th,
.paper-record__table td {
  border: 1px solid #222;
  padding: 0;
  text-align: center;
  vertical-align: middle;
  color: #020817;
}

.paper-record__table thead th {
  background: #fff;
  color: #020817;
  font-family: inherit;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  padding: 5px 4px;
  min-height: 33px;
  overflow: hidden;
  word-break: normal;
}

.paper-record__table tbody {
  font-family: inherit;
  font-size: 13px;
  color: #020817;
}

.paper-record__table tbody td {
  box-sizing: border-box;
  height: 33px;
  position: relative;
  padding: 2px 0;
}

/* 列宽按原 794px 比例换算为百分比，表格随父容器拉伸 */
.paper-record__table th.col-datetime,
.paper-record__table td.col-datetime {
  width: 13%;
  max-width: 0;
  overflow: hidden;
}

.paper-record__table th.col-vital,
.paper-record__table td.col-vital {
  width: 5.54%;
}

.paper-record__table th.col-consciousness,
.paper-record__table td.col-consciousness {
  width: 4.79%;
}

.paper-record__table th.col-pupil,
.paper-record__table td.col-pupil {
  width: 4.28%;
}

.paper-record__table th.col-io-name,
.paper-record__table td.col-io-name {
  width: 5.04%;
}

.paper-record__table th.col-io-amount,
.paper-record__table td.col-io-amount {
  width: 3.02%;
}

.paper-record__table th.col-io-color,
.paper-record__table td.col-io-color {
  width: 4.53%;
}

.paper-record__table th.col-observation,
.paper-record__table td.col-observation {
  width: 21.68%;
}

.paper-record__table th.col-signature,
.paper-record__table td.col-signature {
  width: 8.06%;
}

.paper-field {
  display: block;
  box-sizing: border-box;
  width: 100%;
  border: 0;
  outline: none;
  background: transparent;
  color: #020817;
  font-family: inherit;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  margin: 0;
  padding: 0 2px;
  text-align: center;
  -webkit-text-fill-color: #020817;
}

.paper-field::placeholder {
  color: #020817;
  opacity: 0.35;
}

.paper-field--date {
  height: 14px;
  line-height: 14px;
}

.paper-field--time {
  height: 14px;
  line-height: 14px;
}

.col-datetime :deep(.paper-datetime-picker),
.col-datetime :deep(.paper-datetime-picker.el-input),
.col-datetime :deep(.paper-datetime-picker.el-date-editor) {
  box-sizing: border-box;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  --el-date-editor-width: 100%;
}

.col-datetime :deep(.paper-datetime-picker .el-input__wrapper) {
  box-sizing: border-box;
  width: 100%;
  height: 29px;
  min-height: 29px;
  padding: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  justify-content: center;
}

.col-datetime :deep(.paper-datetime-picker .el-input__inner) {
  width: 100%;
  height: 29px;
  padding: 0 !important;
  line-height: 29px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #020817;
  font-family: inherit;
  font-size: 12px;
  font-weight: 400;
  -webkit-text-fill-color: #020817;
}

.col-datetime :deep(.paper-datetime-picker .el-input__prefix),
.col-datetime :deep(.paper-datetime-picker .el-input__suffix),
.col-datetime :deep(.paper-datetime-picker .el-input__prefix-inner),
.col-datetime :deep(.paper-datetime-picker .el-input__suffix-inner) {
  display: none !important;
}

.col-datetime :deep(.paper-datetime-picker .el-input__inner::placeholder) {
  color: transparent;
}

.col-signature :deep(.paper-signature-select) {
  width: 100%;
}

.col-signature :deep(.el-select__wrapper) {
  box-sizing: border-box;
  min-height: 29px;
  height: 29px;
  padding: 0 2px;
  background: transparent;
  box-shadow: none;
  justify-content: center;
}

.col-signature :deep(.el-select__selection) {
  justify-content: center;
}

/*
 * EP 2.11：已选文案画在 .el-select__placeholder 上（无 is-transparent 时），
 * 禁止整类 placeholder 设透明，否则默认值/选中值都看不见。
 */
.col-signature :deep(.el-select__placeholder),
.col-signature :deep(.el-select__selected-item) {
  text-align: center;
  color: #020817;
  -webkit-text-fill-color: #020817;
  font-family: inherit;
  font-size: 13px;
  font-weight: 400;
}

.col-signature :deep(.el-select__placeholder.is-transparent) {
  color: transparent;
  -webkit-text-fill-color: transparent;
}

.col-signature :deep(.el-select__caret),
.col-signature :deep(.el-select__suffix) {
  display: none;
}

.col-vital .paper-field,
.col-consciousness .paper-field,
.col-pupil .paper-field,
.col-io-amount .paper-field {
  height: 29px;
  line-height: 29px;
}

.col-io-name .paper-field,
.col-io-color .paper-field {
  height: 29px;
  line-height: 29px;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-signature .paper-field {
  height: 29px;
  line-height: 29px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.paper-field--textarea {
  box-sizing: border-box;
  min-height: 29px;
  padding: 3px 4px;
  text-align: left;
  resize: none;
  overflow: hidden;
  word-break: break-all;
  white-space: pre-wrap;
}

.paper-record__table .col-observation {
  vertical-align: top;
}

.paper-field:focus {
  background: #fff;
  outline: none;
}

.paper-row-delete {
  position: absolute;
  top: 1px;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #666;
  cursor: pointer;
}

.paper-row-delete:hover {
  color: #020817;
}

@media print {
  .paper-record__toolbar {
    display: none;
  }

  .paper-record__viewport {
    display: block;
    overflow: visible;
    padding: 0;
  }

  .paper-record__shell {
    width: auto !important;
    height: auto !important;
    overflow: visible;
  }

  .paper-record__sheet.is-fluid {
    width: 100%;
    padding: 0;
  }

  .paper-record__table {
    width: 100%;
  }

  .paper-row-delete {
    display: none;
  }
}
</style>
