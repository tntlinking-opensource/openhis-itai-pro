<script setup lang="ts">
import type { NursingRecordPaperHeader, NursingRecordViewRow } from './nursing-record.types';

import { Delete } from '@element-plus/icons-vue';

import { usePaperSheetFullscreen } from '../../composables/use-paper-sheet-fullscreen';

defineOptions({
  name: 'NursingRecordPaperTable',
});

interface Props {
  paperHeader?: NursingRecordPaperHeader | null;
  rows: NursingRecordViewRow[];
  savingAll?: boolean;
  unsavedCount?: number;
  weekRangeLabel?: string;
}

const props = withDefaults(defineProps<Props>(), {
  paperHeader: null,
  savingAll: false,
  unsavedCount: 0,
  weekRangeLabel: '',
});

const emit = defineEmits<{
  addRow: [];
  deleteRow: [id: string];
  ensureDatetime: [id: string];
  markDirty: [id: string];
  saveAll: [];
}>();

const { isFullscreen, toggleFullscreen } = usePaperSheetFullscreen();

/** 将完整日期格式化为 MM-DD。 */
function formatDateShort(date?: string) {
  if (!date) return '';
  const parts = date.split('-');
  if (parts.length !== 3) return date;
  return `${parts[1]}-${parts[2]}`;
}

/** 同步 MM-DD 输入回完整日期，保留原年份。 */
function syncDateShort(row: NursingRecordViewRow, shortValue: string) {
  const year = row.recordDate?.slice(0, 4) || `${new Date().getFullYear()}`;
  const match = shortValue.match(/^(\d{1,2})-(\d{1,2})$/);
  if (!match) {
    row.recordDate = shortValue;
    emit('markDirty', row.id);
    return;
  }
  const month = match[1]?.padStart(2, '0');
  const day = match[2]?.padStart(2, '0');
  row.recordDate = `${year}-${month}-${day}`;
  emit('markDirty', row.id);
}

/** 字段变更时标记未保存。 */
function handleFieldDirty(row: NursingRecordViewRow) {
  emit('markDirty', row.id);
}
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
    <div class="paper-record" :class="{ 'is-fullscreen': isFullscreen }">
      <div class="paper-record__toolbar">
        <div class="paper-record__toolbar-main">
          <span v-if="weekRangeLabel" class="paper-record__week">{{ weekRangeLabel }}</span>
          <span v-if="unsavedCount > 0" class="paper-record__unsaved"
            >未保存 {{ unsavedCount }} 条</span
          >
        </div>
        <div class="paper-record__toolbar-actions">
          <slot name="toolbar-extra" />
          <ElButton plain @click="toggleFullscreen">
            {{ isFullscreen ? '退出全屏' : '全屏' }}
          </ElButton>
          <ElButton type="primary" plain @click="emit('addRow')">新增行</ElButton>
          <ElButton :loading="savingAll" type="primary" @click="emit('saveAll')">保存</ElButton>
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
                  <span>年龄：{{ paperHeader.age ?? '' }}岁</span>
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
                  <tr v-for="row in rows" :key="row.id" class="paper-record__row">
                    <td class="col-datetime">
                      <input
                        class="paper-field paper-field--date"
                        type="text"
                        data-nav-col="0"
                        data-nav-slot="date"
                        :value="formatDateShort(row.recordDate)"
                        @input="syncDateShort(row, ($event.target as HTMLInputElement).value)"
                      />
                      <input
                        v-model="row.recordTime"
                        class="paper-field paper-field--time"
                        type="text"
                        data-nav-col="0"
                        data-nav-slot="time"
                        @focus="emit('ensureDatetime', row.id)"
                        @input="handleFieldDirty(row)"
                      />
                      <button
                        v-if="row.rowType === 'extra'"
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
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.pulse"
                        class="paper-field"
                        type="text"
                        data-nav-col="2"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.heartRate"
                        class="paper-field"
                        type="text"
                        data-nav-col="3"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.respiratory"
                        class="paper-field"
                        type="text"
                        data-nav-col="4"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-vital">
                      <input
                        v-model="row.bloodPressure"
                        class="paper-field"
                        type="text"
                        data-nav-col="5"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-consciousness">
                      <input
                        v-model="row.consciousness"
                        class="paper-field"
                        type="text"
                        data-nav-col="6"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-pupil">
                      <input
                        v-model="row.pupilLeft"
                        class="paper-field"
                        type="text"
                        data-nav-col="7"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-pupil">
                      <input
                        v-model="row.pupilRight"
                        class="paper-field"
                        type="text"
                        data-nav-col="8"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-name">
                      <input
                        v-model="row.intakeName"
                        class="paper-field"
                        type="text"
                        data-nav-col="9"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-amount">
                      <input
                        v-model="row.intakeAmount"
                        class="paper-field"
                        type="text"
                        data-nav-col="10"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-name">
                      <input
                        v-model="row.outputName"
                        class="paper-field"
                        type="text"
                        data-nav-col="11"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-amount">
                      <input
                        v-model="row.outputAmount"
                        class="paper-field"
                        type="text"
                        data-nav-col="12"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-io-color">
                      <input
                        v-model="row.outputColorCharacter"
                        class="paper-field"
                        type="text"
                        data-nav-col="13"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-observation">
                      <textarea
                        v-model="row.conditionMeasure"
                        class="paper-field paper-field--textarea"
                        rows="1"
                        data-nav-col="14"
                        @input="handleFieldDirty(row)"
                      />
                    </td>
                    <td class="col-signature">
                      <input
                        v-model="row.nurseSignature"
                        class="paper-field"
                        type="text"
                        data-nav-col="15"
                        @input="handleFieldDirty(row)"
                      />
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
  min-height: 33px;
  position: relative;
  padding: 2px 0;
}

/* 列宽按原 794px 比例换算为百分比，表格随父容器拉伸 */
.paper-record__table th.col-datetime,
.paper-record__table td.col-datetime {
  width: 6.55%;
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
  width: 23.68%;
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

.col-datetime .paper-field {
  width: 100%;
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
  height: 29px;
  min-height: 29px;
  padding: 3px 4px;
  text-align: left;
  resize: none;
  overflow: hidden;
  word-break: break-all;
  white-space: normal;
}

.paper-record__table .col-observation {
  vertical-align: middle;
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
