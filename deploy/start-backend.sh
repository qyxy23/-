#!/usr/bin/env bash
# 海龟汤后端生产启动脚本（Linux / 宝塔）
# 用法：./deploy/start-backend.sh
# 可选环境变量：JAR_PATH、JAVA_OPTS、SPRING_PROFILES_ACTIVE、JWT_ADMIN_SECRET_KEY、JWT_USER_SECRET_KEY

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_PATH="${JAR_PATH:-${PROJECT_DIR}/target/HaiGuiTang-*.jar}"
PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -Dfile.encoding=UTF-8}"
PID_FILE="${PROJECT_DIR}/deploy/haiguitang.pid"
LOG_FILE="${PROJECT_DIR}/deploy/haiguitang.log"

resolve_jar() {
  local resolved
  resolved="$(ls -1 ${JAR_PATH} 2>/dev/null | head -n 1 || true)"
  if [[ -z "${resolved}" ]]; then
    echo "未找到 JAR，请先在项目根目录执行: mvn clean package -DskipTests"
    exit 1
  fi
  echo "${resolved}"
}

stop_if_running() {
  if [[ -f "${PID_FILE}" ]]; then
    local old_pid
    old_pid="$(cat "${PID_FILE}")"
    if kill -0 "${old_pid}" 2>/dev/null; then
      echo "停止旧进程 ${old_pid}..."
      kill "${old_pid}"
      sleep 2
    fi
    rm -f "${PID_FILE}"
  fi
}

JAR="$(resolve_jar)"
mkdir -p "${PROJECT_DIR}/deploy"

stop_if_running

echo "启动 HaiGuiTang (${PROFILE}) ..."
nohup java ${JAVA_OPTS} -jar "${JAR}" --spring.profiles.active="${PROFILE}" >> "${LOG_FILE}" 2>&1 &
echo $! > "${PID_FILE}"

echo "已启动 PID=$(cat "${PID_FILE}")"
echo "日志: ${LOG_FILE}"
echo "健康检查: curl -s http://127.0.0.1:8080/user/login >/dev/null && echo OK"
