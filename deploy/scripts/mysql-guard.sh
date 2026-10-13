#!/usr/bin/env bash
# MySQL 家族红线校验（TASK_BOOK §5.2 / conventions/quality-gates.md §5）
# 模式（不区分大小写）：mysql|mariadb|jdbc:mysql|jdbc:mariadb
# 范围：pom.xml、docker-compose*.yml、Flyway 脚本(*.sql)、application*.yml/properties、Dockerfile、Java 源码
# 排除：docs/（禁令性表述属合法提及）、.git、target、node_modules、.tools
# 命中即退出码 1（CI 失败）；唯一修复方式是移除 MySQL 家族依赖/配置，禁止白名单豁免。

set -euo pipefail

cd "$(dirname "$0")/../.."

PATTERN='(mysql|mariadb)'

HITS=$(
  grep -rniE "${PATTERN}" \
    --include='pom.xml' \
    --include='docker-compose*.yml' \
    --include='docker-compose*.yaml' \
    --include='application*.yml' \
    --include='application*.yaml' \
    --include='application*.properties' \
    --include='*.sql' \
    --include='Dockerfile*' \
    --include='*.java' \
    --exclude-dir=docs \
    --exclude-dir=.git \
    --exclude-dir=.tools \
    --exclude-dir=target \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    . || true
)

if [ -n "${HITS}" ]; then
  echo "===== MySQL 红线校验失败（TASK_BOOK §5.2）====="
  echo "${HITS}"
  echo ""
  echo "仅允许 PostgreSQL；请移除上述 MySQL/MariaDB 家族依赖、配置或镜像引用。"
  exit 1
fi

echo "MySQL 红线校验通过：未发现 MySQL/MariaDB 家族引用。"
