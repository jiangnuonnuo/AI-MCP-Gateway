# MCP Gateway 运维部署

`deploy-current-stack.sh` 发布当前仓库的前端、网关后端和增量数据库迁移。它复用主机上已经运行的 MySQL、Redis、Docker 网络和 Nginx 容器，不执行基础设施的 `compose down`、删除、重建或卷操作。

## 首次配置

```bash
cp docs/dev-ops/.env.gateway.example docs/dev-ops/.env.gateway
chmod 600 docs/dev-ops/.env.gateway
# 修改 .env.gateway 中的数据库、Redis、模型密钥和端口配置
```

`GATEWAY_NETWORK` 留空时，脚本从现有网关容器发现网络；如果旧容器不存在，则填写已有基础设施网络名称。`GATEWAY_HOST_PORT` 默认是 `8797`，容器端口是 `8797`；现有 Nginx 通过公网 `8088` 反向代理到该端口。

## 一键更新

```bash
./docs/dev-ops/deploy-current-stack.sh update
```

脚本会先备份 `ai_mcp_gateway_v2`，只执行 `docs/dev-ops/mysql/migrations/` 中尚未记录的增量 SQL，然后备份并同步 `docs/dev-ops/nginx/html`，构建当前 `ai-mcp-gateway-app`，保存旧网关容器，启动新容器，reload 原 Nginx 并验证公网入口。完整备份和回滚状态位于 `.full-stack-state/`，其中的运行时配置和 SQL 备份不可提交到 Git。

生产主机没有 Maven 时，上传构建产物并在 `.env.gateway` 指定：

```bash
GATEWAY_JAR=/home/ubuntu/gw-deploy/ai-mcp-gateway-app.jar
```

## 状态与回滚

```bash
./docs/dev-ops/deploy-current-stack.sh status
./docs/dev-ops/deploy-current-stack.sh rollback
```

回滚会恢复最近一次前端快照和旧网关容器，并重新验证 Nginx 公网入口。数据库不会被自动回滚；脚本会报告对应 SQL 备份路径，由运维人员在确认后执行恢复。

## 远程执行前提

目标主机必须允许 `ubuntu` 通过 SSH 登录，并且该账号能够调用 Docker。当前仓库脚本不保存 SSH 密码；推荐使用 SSH 密钥。部署前先在主机上确认 `docker info`、Maven/Java、目标网关容器名称、Docker 网络名称以及 `.env.gateway` 中的运行时密钥。若 SSH 登录失败，脚本不会尝试修改远端容器。

当目标主机没有 Maven 时，先上传新构建的 JAR 并设置 `GATEWAY_JAR`，脚本会直接构建镜像。

## 控制台登录

前端登录调用 `/api-gateway/console/auth/login`。体验账号固定为 `user / user`；管理员账号默认为 `admin`，管理员密码必须通过 `MCP_CONSOLE_ADMIN_PASSWORD` 注入运行时环境。生产环境不要把真实密码写入仓库或命令行参数。
