package cn.bugstack.ai.cases.mcp.message.node;

import cn.bugstack.ai.cases.mcp.message.AbstractMcpMessageServiceSupport;
import cn.bugstack.ai.cases.mcp.message.factory.DefaultMcpMessageFactory;
import cn.bugstack.ai.domain.session.model.entity.HandleMessageCommandEntity;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 根节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/2/20 08:00
 */
@Slf4j
@Service("mcpMessageRootNode")
public class RootNode extends AbstractMcpMessageServiceSupport {

    @Resource(name = "mcpMessageSessionNode")
    private SessionNode sessionNode;

    @Override
    protected ResponseEntity<Void> doApply(HandleMessageCommandEntity requestParameter, DefaultMcpMessageFactory.DynamicContext dynamicContext) throws Exception {
        try {
            log.info("消息处理 mcp message RootNode:{}", requestParameter);

            return router(requestParameter, dynamicContext);
        } catch (Exception e) {
            log.error("消息处理 mcp message RootNode:{}", requestParameter, e);
            throw e;
        }
    }

    @Override
    public StrategyHandler<HandleMessageCommandEntity, DefaultMcpMessageFactory.DynamicContext, ResponseEntity<Void>> get(HandleMessageCommandEntity requestParameter, DefaultMcpMessageFactory.DynamicContext dynamicContext) throws Exception {
        return sessionNode;
    }

}
