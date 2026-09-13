/**
 * 全模块统一的异常契约。
 *
 * <p>基础异常、业务异常和技术异常的公共错误码均在此定义；Domain、Case、
 * Infrastructure 和 Trigger 不得各自建立异常实现包。异常对象不得依赖具体业务模块、
 * 基础设施实现或 Spring 类型。</p>
 */
package cn.bugstack.ai.types.exception;
