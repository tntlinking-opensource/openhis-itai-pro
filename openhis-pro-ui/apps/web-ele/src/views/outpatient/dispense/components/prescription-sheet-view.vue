<script setup lang="ts">
import type { PrescriptionSheetData } from './prescription-sheet.types';

defineOptions({
  name: 'PrescriptionSheetView',
});

const props = withDefaults(
  defineProps<{
    /** 为 true 时略增大内边距与药品区高度，便于弹窗内阅读 */
    comfortable?: boolean;
    data: PrescriptionSheetData;
  }>(),
  {
    comfortable: false,
  },
);
</script>

<template>
  <!-- 布局与样式对齐历史「处方单」弹窗稿；数据仍走 PrescriptionSheetData，便于队列/打印复用 -->
  <div
    class="prescription-dialog-wrapper"
    :class="{ 'prescription-dialog-wrapper--comfortable': props.comfortable }"
  >
    <div class="prescription-container">
      <div>
        <span>处方号：</span>
        <span>{{ props.data.prescriptionNo }}</span>
      </div>
      <div class="prescription-sheet-title-center">
        <h2>{{ props.data.hospitalName }}</h2>
      </div>
      <div class="prescription-sheet-title-center">
        <h3>处方单</h3>
      </div>
      <div class="prescription-sheet-row-between">
        <div class="prescription-sheet-field">
          <span class="item-label">姓名：</span>
          <span class="item-value">{{ props.data.patientName }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">年龄：</span>
          <span class="item-value">{{ props.data.patientAgeLabel }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">性别：</span>
          <span class="item-value">{{ props.data.patientGenderLabel }}</span>
        </div>
      </div>
      <div class="divider"></div>
      <div class="prescription-sheet-row-between">
        <div class="prescription-sheet-field">
          <span class="item-label">科室：</span>
          <span class="item-value">{{ props.data.deptName }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">费用性质：</span>
          <span class="item-value">{{ props.data.feeNatureLabel }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">日期：</span>
          <span class="item-value">{{ props.data.prescriptionDate }}</span>
        </div>
      </div>
      <div class="divider"></div>
      <div class="prescription-sheet-row-between">
        <div class="prescription-sheet-field prescription-sheet-field--grow">
          <span class="item-label">门诊号：</span>
          <span class="item-value item-value--outpatient">{{ props.data.outpatientNoLabel }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">开单医生：</span>
          <span class="item-value">{{ props.data.prescribingDoctorName }}</span>
        </div>
      </div>
      <div class="divider"></div>
      <div class="prescription-sheet-row-between prescription-sheet-diagnosis-row">
        <div class="prescription-sheet-field prescription-sheet-field--block">
          <span class="item-label">诊断：</span>
          <span class="item-value item-value--diagnosis">{{ props.data.diagnosis }}</span>
        </div>
      </div>
      <div class="divider"></div>
      <div class="rp-heading">Rp</div>
      <div class="medicen-list">
        <div v-for="(line, idx) in props.data.lines ?? []" :key="idx" class="medicine-line">
          <span>{{ idx + 1 }}.</span>
          <span>{{ line.title }}</span>
          <div v-if="line.usage" class="medicine-usage">
            <span>{{ line.usage }}</span>
          </div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="prescription-sheet-row-between">
        <div class="prescription-sheet-field">
          <span class="item-label">医师：</span>
          <span class="item-value">{{ props.data.footerDoctorName }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">合计：</span>
          <span class="item-value">{{ props.data.totalAmountLabel }}</span>
        </div>
      </div>
      <div class="prescription-sheet-row-between prescription-sheet-footer-second">
        <div class="prescription-sheet-field">
          <span class="item-label">调配：</span>
          <span class="item-value">{{ props.data.footerDispensingName }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">发药：</span>
          <span class="item-value">{{ props.data.footerDeliverName }}</span>
        </div>
        <div class="prescription-sheet-field">
          <span class="item-label">划价：</span>
          <span class="item-value">{{ props.data.footerPricingClerkName }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.prescription-dialog-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: center;
  width: 100%;
  min-height: 0;
  overflow-x: auto;
  background-color: transparent;
}

.prescription-dialog-wrapper--comfortable {
  .prescription-container {
    padding: 14px;
  }

  .medicen-list {
    min-height: 380px;
  }
}

.prescription-container {
  box-sizing: border-box;
  width: 500px;
  max-width: 100%;
  min-height: 660px;
  padding: 10px;
  border: solid 2px #757575;
  font-size: 13px;
  color: #000;
  /* 衬纸灰仅在此框内；外层 wrapper 透明 */
  background-color: #e4e4e4;
}

.prescription-sheet-title-center {
  text-align: center;

  h2 {
    margin: 0.35em 0 0;
    font-size: 1.35rem;
    font-weight: 700;
    color: #000;
  }

  h3 {
    margin: 0.25em 0 0;
    font-size: 1.1rem;
    font-weight: 700;
    color: #000;
  }
}

.prescription-sheet-row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

/** 单行内「标签 + 值」左右排列，禁止压成上下两行 */
.prescription-sheet-field {
  display: flex;
  flex-flow: row nowrap;
  align-items: center;
  gap: 2px;
  min-width: 0;
  flex: 1 1 0;
}

.prescription-sheet-field--grow {
  flex: 1.35 1 0;
}

.prescription-sheet-field--block {
  flex: 1 1 100%;
}

.prescription-sheet-diagnosis-row {
  align-items: center;
}

.prescription-sheet-footer-second {
  margin-top: 8px;
}

.divider {
  height: 2px;
  margin: 5px 0;
  background-color: #757575;
}

.rp-heading {
  margin-bottom: 3px;
  font-size: 16px;
  font-weight: 700;
}

.medicen-list {
  box-sizing: border-box;
  min-height: 330px;
  padding-right: 2px;
  overflow-y: auto;
}

.medicine-line {
  margin-bottom: 3px;
}

.medicine-usage {
  margin-top: 10px;
  margin-left: 50px;
}

.item-label {
  flex: 0 0 auto;
  font-weight: 700;
  color: #000;
  text-align: left;
  white-space: nowrap;
}

.item-value {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 500;
  color: #393a3b;
}

.item-value--outpatient {
  overflow-wrap: anywhere;
  word-break: break-all;
}

.item-value--diagnosis {
  overflow-wrap: anywhere;
  word-break: break-all;
}
</style>
