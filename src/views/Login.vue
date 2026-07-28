<template>
  <div class="login-page">
    <div class="visual-side">
      <div class="line-dot dot-a"></div>
      <div class="line-dot dot-b"></div>
      <div class="tech-stage">
        <div class="screen">
          <div class="chart-line"></div>
          <div class="panel-lines">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
        <div class="platform"></div>
        <div class="cube cube-a"></div>
        <div class="cube cube-b"></div>
        <div class="glow-card"></div>
      </div>
    </div>
    <el-card class="login-card" shadow="never">
      <div class="login-title">
        <h1>LexPro</h1>
        <p>法律大模型与检察业务融合应用系统</p>
      </div>
      <el-form :model="form" class="login-form" @submit.prevent>
        <el-form-item>
          <el-input v-model="form.account" size="large" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="请输入登录密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <div class="form-options">
          <el-checkbox v-model="form.remember">记住密码</el-checkbox>
          <el-link type="info" :underline="false">忘记密码</el-link>
        </div>
        <el-button type="primary" size="large" class="login-button" @click="login">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const form = reactive({
  account: 'admin',
  password: '123456',
  remember: false
})

const login = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  grid-template-columns: minmax(560px, 1fr) 520px;
  align-items: center;
  min-height: 100vh;
  padding: 64px 11vw;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 92%, rgba(63, 196, 255, 0.34), transparent 15%),
    linear-gradient(135deg, #228aff 0%, #4255f4 58%, #5b34ec 100%);
}

.login-page::before,
.login-page::after {
  position: absolute;
  width: 190px;
  height: 380px;
  background: rgba(255, 255, 255, 0.1);
  clip-path: polygon(50% 0, 100% 14%, 100% 100%, 0 86%, 0 14%);
  content: "";
}

.login-page::before {
  right: 20%;
  top: 4%;
}

.login-page::after {
  right: 3%;
  bottom: 8%;
  width: 88px;
  height: 260px;
}

.visual-side {
  position: relative;
  height: 520px;
}

.line-dot {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 50%;
}

.dot-a {
  top: 26px;
  left: 16px;
  width: 78px;
  height: 78px;
  box-shadow: 0 0 0 14px rgba(255, 255, 255, 0.08);
}

.dot-b {
  right: 90px;
  top: 90px;
  width: 54px;
  height: 54px;
}

.dot-a::after,
.dot-b::after {
  position: absolute;
  height: 1px;
  background: rgba(255, 255, 255, 0.42);
  transform-origin: left;
  content: "";
}

.dot-a::after {
  left: 58px;
  top: 52px;
  width: 190px;
  transform: rotate(38deg);
}

.dot-b::after {
  left: 40px;
  top: 18px;
  width: 120px;
  transform: rotate(-36deg);
}

.tech-stage {
  position: absolute;
  left: 70px;
  top: 118px;
  width: 500px;
  height: 330px;
  transform: skewY(-8deg);
}

.platform {
  position: absolute;
  left: 90px;
  top: 150px;
  width: 310px;
  height: 150px;
  background: linear-gradient(135deg, rgba(21, 93, 233, 0.84), rgba(16, 194, 255, 0.32));
  border: 1px solid rgba(114, 222, 255, 0.42);
  box-shadow: 0 30px 60px rgba(9, 44, 140, 0.28);
  transform: rotateX(58deg) rotateZ(-35deg);
}

.screen {
  position: absolute;
  left: 135px;
  top: 24px;
  z-index: 2;
  width: 180px;
  height: 126px;
  background: rgba(255, 255, 255, 0.92);
  border: 8px solid rgba(44, 117, 255, 0.72);
  border-radius: 8px;
  box-shadow: 0 20px 40px rgba(4, 42, 136, 0.3);
}

.chart-line {
  position: absolute;
  left: 22px;
  top: 40px;
  width: 78px;
  height: 38px;
  border-bottom: 3px solid #72d7ff;
  border-left: 3px solid #72d7ff;
}

.chart-line::after {
  position: absolute;
  left: 12px;
  top: -4px;
  width: 92px;
  height: 34px;
  border-top: 4px solid #5a7dff;
  transform: skewY(-16deg);
  content: "";
}

.panel-lines {
  position: absolute;
  right: 18px;
  top: 30px;
  display: grid;
  gap: 10px;
  width: 42px;
}

.panel-lines span {
  height: 6px;
  background: #ff5c6c;
  border-radius: 4px;
}

.panel-lines span:nth-child(2) {
  background: #2f72f6;
}

.panel-lines span:nth-child(3) {
  background: #ffa940;
}

.cube,
.glow-card {
  position: absolute;
  background: rgba(47, 223, 255, 0.75);
  border: 1px solid rgba(152, 240, 255, 0.6);
  box-shadow: 0 0 30px rgba(25, 219, 255, 0.3);
}

.cube-a {
  left: 70px;
  bottom: 62px;
  width: 42px;
  height: 42px;
}

.cube-b {
  left: 410px;
  bottom: 88px;
  width: 54px;
  height: 54px;
}

.glow-card {
  left: 300px;
  top: 168px;
  width: 150px;
  height: 76px;
  background: rgba(47, 223, 255, 0.36);
  border-radius: 8px;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 430px;
  padding: 30px 38px 34px;
  border: 0;
  border-radius: 28px;
  justify-self: end;
}

.login-title {
  margin: 8px 0 40px;
}

.login-title h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.login-title p {
  margin: 8px 0 0;
  font-size: 20px;
  font-weight: 700;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 0;
  box-shadow: 0 1px 0 #c8d0dc;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 0 24px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
}
</style>
