/**
 * Domain Port 和既有 ToolExecutor 的外部能力适配器。
 *
 * <p>仓储/Registry 实现放在 {@code adapter.repository}；指标和参数绑定等
 * 不实现 Domain Port 的技术组件放在各自基础设施包，异常类型统一位于
 * {@code cn.bugstack.ai.types.exception}。</p>
 */
package cn.bugstack.ai.infrastructure.adapter.port;
