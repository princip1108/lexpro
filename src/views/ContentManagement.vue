<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">内容管理</h2>
      <div class="toolbar">
        <el-button :icon="Upload">导入内容</el-button>
        <el-button type="primary" :icon="Plus">新增条目</el-button>
      </div>
    </div>

    <div class="content-stats">
      <SectionCard v-for="item in data.stats" :key="item.label">
        <div class="content-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <em>{{ item.trend }}</em>
        </div>
      </SectionCard>
    </div>

    <div class="content-layout">
      <SectionCard title="内容分类">
        <div class="category-list">
          <div v-for="item in data.categories" :key="item.name" class="category-item">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.owner }}</p>
            </div>
            <span>{{ item.count }} 条</span>
            <el-tag :type="item.status === '已发布' ? 'success' : 'warning'">{{ item.status }}</el-tag>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="最近更新">
        <el-table :data="data.recent" stripe>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="editor" label="编辑人" width="90" />
          <el-table-column prop="time" label="更新时间" width="160" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType[row.status]">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </SectionCard>
    </div>
  </div>
</template>

<script setup>
import { Plus, Upload } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/contentManagement.json'

const statusType = {
  已发布: 'success',
  待审核: 'warning',
  维护中: 'primary'
}
</script>

<style scoped>
.content-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.content-stat {
  position: relative;
  display: grid;
  gap: 8px;
}

.content-stat span {
  color: #64748b;
}

.content-stat strong {
  color: var(--primary-deep);
  font-size: 30px;
}

.content-stat em {
  position: absolute;
  top: 4px;
  right: 0;
  color: #15b8a6;
  font-style: normal;
  font-weight: 700;
}

.content-layout {
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.category-list {
  display: grid;
  gap: 12px;
}

.category-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px 78px;
  align-items: center;
  gap: 10px;
  padding: 15px;
  background: #f7f9fd;
  border: 1px solid #edf1f7;
  border-radius: 6px;
}

.category-item strong {
  display: block;
  margin-bottom: 6px;
}

.category-item p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.category-item span {
  color: #3f5268;
  font-weight: 700;
}
</style>
