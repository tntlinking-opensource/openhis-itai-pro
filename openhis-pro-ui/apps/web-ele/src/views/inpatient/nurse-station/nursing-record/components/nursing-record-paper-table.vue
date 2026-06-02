<script setup lang="ts">
import type { NursingRecordViewRow } from './nursing-record.types';

import { computed } from 'vue';

import { Delete } from '@element-plus/icons-vue';

interface Props {
  rows: NursingRecordViewRow[];
}

interface PaperDisplayRow extends NursingRecordViewRow {
  showDate: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  addRow: [];
  deleteRow: [id: string];
  print: [];
  saveAll: [];
}>();

defineOptions({
  name: 'NursingRecordPaperTable',
});

/** 同日期合并首列，避免重复显示日期。 */
const displayRows = computed<PaperDisplayRow[]>(() => {
  const result: PaperDisplayRow[] = [];
  let index = 0;

  while (index < props.rows.length) {
    const currentDate = props.rows[index]?.recordDate ?? '';
    let span = 1;

    while (
      index + span < props.rows.length &&
      props.rows[index + span]?.recordDate === currentDate
    ) {
      span += 1;
    }

    for (let offset = 0; offset < span; offset += 1) {
      const row = props.rows[index + offset];
      if (!row) continue;
      result.push({
        ...row,
        showDate: offset === 0,
      });
    }

    index += span;
  }

  return result;
});

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
    return;
  }
  const month = match[1]?.padStart(2, '0');
  const day = match[2]?.padStart(2, '0');
  row.recordDate = `${year}-${month}-${day}`;
}
</script>

<template>
  <div class="paper-record">
    <div class="paper-record__toolbar">
      <span class="paper-record__title">护理记录单</span>
      <div class="paper-record__toolbar-actions">
        <ElButton size="small" type="primary" plain @click="emit('addRow')">新增行</ElButton>
        <ElButton size="small" type="primary" @click="emit('saveAll')">保存</ElButton>
        <ElButton size="small" plain @click="emit('print')">打印</ElButton>
      </div>
    </div>

    <div class="paper-record__scroll">
      <table class="paper-record__table">
        <colgroup>
          <col class="col-datetime" />
          <col class="col-consciousness" />
          <col class="col-vital" span="6" />
          <col class="col-io-name" />
          <col class="col-io-amount" />
          <col class="col-io-name" />
          <col class="col-io-amount" />
          <col class="col-io-color" />
          <col class="col-care" />
          <col class="col-care" />
          <col class="col-reserved" />
          <col class="col-reserved" />
          <col class="col-observation" />
          <col class="col-signature" />
        </colgroup>
        <thead>
          <tr>
            <th class="col-datetime">日期</th>
            <th class="col-consciousness" rowspan="2">意识</th>
            <th class="col-vital">体温</th>
            <th class="col-vital">脉搏</th>
            <th class="col-vital">呼吸</th>
            <th class="col-vital">血压</th>
            <th class="col-vital">血氧饱和度</th>
            <th class="col-vital">吸氧</th>
            <th class="col-group" colspan="2">入量</th>
            <th class="col-group" colspan="3">出量</th>
            <th class="col-care" rowspan="2">皮肤情况</th>
            <th class="col-care" rowspan="2">管路护理</th>
            <th class="col-reserved" rowspan="2"></th>
            <th class="col-reserved" rowspan="2"></th>
            <th class="col-observation" rowspan="2">病情观察及措施</th>
            <th class="col-signature" rowspan="2">护士签名</th>
          </tr>
          <tr>
            <th class="col-datetime">时间</th>
            <th class="col-vital">℃</th>
            <th class="col-vital">次/分</th>
            <th class="col-vital">次/分</th>
            <th class="col-vital">mmHg</th>
            <th class="col-vital">%</th>
            <th class="col-vital">L/min</th>
            <th class="col-io-name">名称</th>
            <th class="col-io-amount">ml</th>
            <th class="col-io-name">名称</th>
            <th class="col-io-amount">ml</th>
            <th class="col-io-color">颜色性状</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in displayRows" :key="row.id" class="paper-record__row">
            <td class="col-datetime">
              <input
                v-if="row.showDate"
                class="paper-field paper-field--date"
                type="text"
                :value="formatDateShort(row.recordDate)"
                @input="syncDateShort(row, ($event.target as HTMLInputElement).value)"
              />
              <input v-model="row.recordTime" class="paper-field paper-field--time" type="text" />
              <button
                type="button"
                class="paper-row-delete"
                title="删除此行"
                @click="emit('deleteRow', row.id)"
              >
                <ElIcon :size="12"><Delete /></ElIcon>
              </button>
            </td>
            <td class="col-consciousness">
              <input
                v-model="row.consciousness"
                class="paper-field"
                type="text"
                list="consciousness-list"
              />
            </td>
            <td class="col-vital">
              <input v-model="row.temperature" class="paper-field" type="text" />
            </td>
            <td class="col-vital">
              <input v-model="row.pulse" class="paper-field" type="text" />
            </td>
            <td class="col-vital">
              <input v-model="row.respiratory" class="paper-field" type="text" />
            </td>
            <td class="col-vital">
              <input v-model="row.bloodPressure" class="paper-field" type="text" />
            </td>
            <td class="col-vital">
              <input v-model="row.oxygenSaturation" class="paper-field" type="text" />
            </td>
            <td class="col-vital">
              <input v-model="row.oxygenRate" class="paper-field" type="text" />
            </td>
            <td class="col-io-name">
              <input v-model="row.intakeName" class="paper-field" type="text" />
            </td>
            <td class="col-io-amount">
              <input v-model="row.intakeAmount" class="paper-field" type="text" />
            </td>
            <td class="col-io-name">
              <input v-model="row.outputName" class="paper-field" type="text" />
            </td>
            <td class="col-io-amount">
              <input v-model="row.outputAmount" class="paper-field" type="text" />
            </td>
            <td class="col-io-color">
              <input v-model="row.outputColorCharacter" class="paper-field" type="text" />
            </td>
            <td class="col-care">
              <input
                v-model="row.skinStatus"
                class="paper-field"
                type="text"
                list="skin-status-list"
              />
            </td>
            <td class="col-care">
              <input v-model="row.tubeCare" class="paper-field" type="text" list="tube-care-list" />
            </td>
            <td class="col-reserved">
              <input v-model="row.reservedCol1" class="paper-field" type="text" />
            </td>
            <td class="col-reserved">
              <input v-model="row.reservedCol2" class="paper-field" type="text" />
            </td>
            <td class="col-observation">
              <textarea
                v-model="row.conditionMeasure"
                class="paper-field paper-field--textarea"
                rows="1"
              />
            </td>
            <td class="col-signature">
              <input v-model="row.nurseSignature" class="paper-field" type="text" />
            </td>
          </tr>
        </tbody>
      </table>

      <datalist id="consciousness-list">
        <option value="清醒" />
        <option value="嗜睡" />
        <option value="模糊" />
        <option value="谵妄" />
        <option value="昏睡" />
        <option value="昏迷" />
      </datalist>
      <datalist id="skin-status-list">
        <option value="完整" />
        <option value="潮红" />
        <option value="压红" />
        <option value="破损" />
        <option value="渗液" />
        <option value="已换药" />
      </datalist>
      <datalist id="tube-care-list">
        <option value="无" />
        <option value="胃管" />
        <option value="导尿管" />
        <option value="静脉通路" />
        <option value="引流管" />
        <option value="气管插管" />
      </datalist>
    </div>
  </div>
</template>

<style scoped>
.paper-record {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  min-height: 0;
}

.paper-record__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px;
}

.paper-record__title {
  color: var(--el-text-color-primary);
  font-family: SimSun, 'Songti SC', serif;
  font-size: 14px;
  font-weight: 600;
}

.paper-record__toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.paper-record__scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: #fff;
  border: 1px solid #000;
}

.paper-record__table {
  width: 100%;
  min-width: 1480px;
  border-collapse: collapse;
  table-layout: fixed;
  font-family: SimSun, 'Songti SC', serif;
  font-size: 12px;
  color: #000;
}

.paper-record__table th,
.paper-record__table td {
  border: 1px solid #000;
  padding: 0;
  text-align: center;
  vertical-align: middle;
}

.paper-record__table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.15;
  padding: 3px 1px;
}

.paper-record__table thead tr:last-child th {
  top: 24px;
  font-size: 11px;
  font-weight: 500;
}

.paper-record__table td {
  height: 26px;
  position: relative;
}

.col-datetime {
  width: 52px;
  padding-right: 12px !important;
}

.col-date,
.col-time {
  width: 52px;
}

.paper-field--date {
  height: 12px;
  font-size: 11px;
  line-height: 12px;
}

.paper-field--time {
  height: 12px;
  font-size: 11px;
  line-height: 12px;
}

.col-datetime .paper-field--time:only-child {
  height: 24px;
  line-height: 24px;
}

.col-consciousness {
  width: 52px;
}

.col-vital {
  width: 48px;
}

.col-io-name {
  width: 60px;
}

.col-io-amount {
  width: 40px;
}

.col-io-color {
  width: 68px;
}

.col-care {
  width: 68px;
}

.col-reserved {
  width: 20px;
}

.col-observation {
  width: 220px;
}

.col-signature {
  width: 64px;
}

.paper-field {
  display: block;
  width: 100%;
  height: 24px;
  border: 0;
  outline: none;
  background: transparent;
  color: #000;
  font-family: inherit;
  font-size: 12px;
  line-height: 24px;
  padding: 0 2px;
  margin: 0;
  text-align: center;
}

.paper-field:focus {
  background: rgb(255 255 255 / 90%);
  outline: 1px dashed #666;
  outline-offset: -1px;
}

.paper-field--textarea {
  height: auto;
  min-height: 24px;
  line-height: 1.25;
  padding: 2px 4px;
  text-align: left;
  resize: none;
  overflow: hidden;
}

.col-observation .paper-field--textarea {
  min-height: 24px;
}

.paper-row-delete {
  position: absolute;
  top: 50%;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #999;
  cursor: pointer;
  opacity: 0;
  transform: translateY(-50%);
}

.paper-record__row:hover .paper-row-delete {
  opacity: 1;
}

.paper-row-delete:hover {
  color: #c00;
}

@media print {
  .paper-record__toolbar {
    display: none;
  }

  .paper-row-delete {
    display: none;
  }

  .paper-field:focus {
    outline: none;
  }
}
</style>
