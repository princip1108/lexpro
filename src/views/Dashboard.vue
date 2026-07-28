<template>
  <div class="page dashboard-page">
    <div class="dashboard-grid">
      <div class="left-col">
        <section class="welcome-card">
          <div class="weather-icon">
            <span></span>
          </div>
          <strong>{{ user.welcome }}</strong>
        </section>

        <SectionCard title="应用中心">
          <template #extra>
            <el-link type="primary" :underline="false">更多</el-link>
          </template>
          <div class="app-grid">
            <AppIconCard
              v-for="app in apps"
              :key="app.title"
              :title="app.title"
              :color="app.color"
              :icon="iconMap[app.icon]"
              @select="router.push(app.path)"
            />
          </div>
        </SectionCard>

        <SectionCard title="待办事项统计">
          <div class="stats-row">
            <StatCard
              v-for="item in stats.todo"
              :key="item.label"
              :label="item.label"
              :display="item.display"
              :value="item.value"
              :color="item.color"
            />
          </div>
        </SectionCard>

        <div class="two-col">
          <SectionCard title="待办案件列表">
            <el-tabs model-value="todo" class="case-tabs">
              <el-tab-pane label="待办案件" name="todo">
                <div class="todo-list">
                  <div v-for="item in todoCases.slice(0, 5)" :key="item.caseNo" class="todo-item">
                    <span class="status-dot"></span>
                    <div>
                      <strong>{{ item.name }}</strong>
                      <p>{{ item.source }} · {{ item.time }}</p>
                    </div>
                    <el-button size="small" type="primary" link @click="router.push('/review-report')">
                      查看文章
                    </el-button>
                  </div>
                </div>
              </el-tab-pane>
              <el-tab-pane label="结办案件" name="done"></el-tab-pane>
            </el-tabs>
          </SectionCard>

          <SectionCard title="案件分类统计">
            <div class="donut-row">
              <div class="donut">
                <div>
                  <strong>74</strong>
                  <span>分类总数</span>
                </div>
              </div>
              <div class="legend-list">
                <div v-for="item in stats.categories" :key="item.name" class="legend-item">
                  <span :style="{ background: item.color }"></span>
                  <label>{{ item.name }}</label>
                  <strong>{{ item.count }}</strong>
                  <em>{{ ((item.count / 74) * 100).toFixed(2) }}%</em>
                </div>
              </div>
            </div>
          </SectionCard>
        </div>
      </div>

      <div class="right-col">
        <SectionCard title="通知公告">
          <template #extra>
            <el-link type="primary" :underline="false">更多</el-link>
          </template>
          <div class="notice-list">
            <div v-for="item in announcements" :key="item.title" class="notice-item">
              <span>{{ item.title }}</span>
              <time>{{ item.date }}</time>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="日程安排">
          <el-timeline class="schedule-line">
            <el-timeline-item
              v-for="item in schedules"
              :key="item.time + item.title"
              :timestamp="item.time"
              placement="top"
              type="primary"
            >
              <div class="schedule-card">
                <strong>{{ item.title }}</strong>
                <span>{{ item.desc }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </SectionCard>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import {
  Aim,
  CollectionTag,
  DocumentChecked,
  Files,
  FolderOpened,
  Memo,
  Reading,
  Search,
  Tickets
} from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import StatCard from '../components/common/StatCard.vue'
import AppIconCard from '../components/common/AppIconCard.vue'
import user from '../mock/user.json'
import apps from '../mock/appCenter.json'
import stats from '../mock/dashboardStats.json'
import todoCases from '../mock/todoCases.json'
import announcements from '../mock/announcements.json'
import schedules from '../mock/schedules.json'

const router = useRouter()
const iconMap = {
  Aim,
  CollectionTag,
  DocumentChecked,
  Files,
  FolderOpened,
  Memo,
  Reading,
  Search,
  Tickets
}
</script>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 18px;
}

.left-col,
.right-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-card {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 86px;
  padding: 0 26px;
  color: #fff;
  background: linear-gradient(90deg, #2f72f6 0%, #3679ff 100%);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(47, 114, 246, 0.22);
}

.welcome-card strong {
  font-size: 18px;
}

.weather-icon {
  position: relative;
  width: 58px;
  height: 42px;
}

.weather-icon::before {
  position: absolute;
  right: 8px;
  top: 0;
  width: 30px;
  height: 30px;
  background: #ffb83e;
  border-radius: 50%;
  box-shadow: 0 0 14px rgba(255, 184, 62, 0.45);
  content: "";
}

.weather-icon span {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 56px;
  height: 28px;
  background: linear-gradient(180deg, #eef6ff, #9fd0ff);
  border-radius: 24px;
}

.app-grid,
.stats-row {
  display: flex;
  gap: 12px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1.25fr;
  gap: 16px;
}

.case-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.todo-list {
  display: grid;
  gap: 12px;
}

.todo-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  background: #ff4d5f;
  border-radius: 50%;
}

.todo-item strong {
  display: block;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-item p {
  margin: 4px 0 0;
  color: #7b8794;
  font-size: 12px;
}

.donut-row {
  display: grid;
  grid-template-columns: 210px 1fr;
  align-items: center;
  gap: 20px;
}

.donut {
  display: grid;
  width: 190px;
  height: 190px;
  margin: 0 auto;
  background: conic-gradient(#2f72f6 0 30%, #14b8a6 30% 50%, #49b735 50% 66%, #ff8a00 66% 82%, #e83e8c 82% 100%);
  border-radius: 50%;
  place-items: center;
}

.donut::before {
  position: absolute;
  width: 104px;
  height: 104px;
  background: #fff;
  border-radius: 50%;
  content: "";
}

.donut div {
  position: relative;
  z-index: 1;
  text-align: center;
}

.donut strong,
.donut span {
  display: block;
}

.donut strong {
  font-size: 30px;
}

.donut span {
  font-size: 12px;
  color: #6b7888;
}

.legend-list {
  display: grid;
  gap: 12px;
}

.legend-item {
  display: grid;
  grid-template-columns: 10px 1fr 36px 70px;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 13px;
}

.legend-item span {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend-item strong {
  color: #1f2d3d;
}

.legend-item em {
  font-style: normal;
}

.notice-list {
  display: grid;
}

.notice-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 92px;
  align-items: center;
  min-height: 58px;
  border-bottom: 1px solid #eef2f7;
}

.notice-item span {
  overflow: hidden;
  color: #3a4a5e;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item time {
  color: #8492a6;
  font-size: 12px;
  text-align: right;
}

.schedule-line {
  padding-left: 2px;
}

.schedule-card {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  background: #eef6ff;
  border-radius: 6px;
}

.schedule-card strong {
  color: #304057;
  font-size: 14px;
}

.schedule-card span {
  color: #7b8794;
  font-size: 12px;
}
</style>
