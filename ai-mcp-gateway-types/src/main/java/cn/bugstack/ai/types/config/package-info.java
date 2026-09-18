/**
 * 跨模块配置契约。
 *
 * <p>本包只保留稳定、无框架依赖的 App Config 到 Infrastructure 共享接口。
 * JDBC 地址、运行时凭证、数据源解析和连接池协作契约属于 Infrastructure，不能放入本包。</p>
 */
package cn.bugstack.ai.types.config;
