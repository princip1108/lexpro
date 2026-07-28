<template>
  <el-card class="case-card" shadow="hover">
    <div class="case-head">
      <div>
        <h3>{{ item.title }}</h3>
        <p>{{ item.caseNo }}</p>
      </div>
      <el-tag type="success" effect="light">{{ item.matchRate }}</el-tag>
    </div>
    <div class="case-meta">
      <el-tag>{{ item.caseType }}</el-tag>
      <el-tag type="warning">{{ item.caseLevel }}</el-tag>
      <el-tag type="info">{{ item.courtLevel }}</el-tag>
      <span>{{ item.courtName }}</span>
      <span>{{ item.judgmentDate }}</span>
    </div>
    <p class="summary">{{ item.summary }}</p>
    <div class="focus">
      <strong>争议焦点：</strong>
      <span>{{ item.disputeFocus }}</span>
    </div>
    <div class="card-actions">
      <slot name="actions" />
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  item: {
    type: Object,
    required: true
  }
})
</script>

<style scoped>
.case-card {
  border: 1px solid #edf1f7;
}

.case-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.case-head h3 {
  margin: 0 0 6px;
  font-size: 17px;
}

.case-head p,
.summary {
  margin: 0;
  color: #64748b;
}

.case-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
  color: #64748b;
  font-size: 13px;
}

.summary {
  line-height: 1.7;
}

.focus {
  margin-top: 12px;
  padding: 12px;
  line-height: 1.7;
  background: #f7f9fd;
  border-radius: 6px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
</style>
