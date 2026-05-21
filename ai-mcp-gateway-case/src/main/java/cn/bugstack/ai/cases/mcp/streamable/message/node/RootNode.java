package cn.bugstack.ai.cases.mcp.streamable.message.node;

import cn.bugstack.ai.cases.mcp.streamable.message.AbstractMcpStreamableMessageServiceSupport;
import cn.bugstack.ai.cases.mcp.streamable.message.factory.DefaultMcpStreamableMessageFactory;
import cn.bugstack.ai.domain.session.model.entity.HandleMessageCommandEntity;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 根节点
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/5/20 07:36
 */
@Slf4j
@Service("mcpStreamableMessageRootNode")
public class RootNode extends AbstractMcpStreamableMessageServiceSupport {

    @Resource(name = "mcpStreamableMessageSessionNode")
    private SessionNode sessionNode;

    @Override
    protected ResponseEntity<Void> doApply(HandleMessageCommandEntity requestParameter, DefaultMcpStreamableMessageFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

    @Override
    public StrategyHandler<HandleMessageCommandEntity, DefaultMcpStreamableMessageFactory.DynamicContext, ResponseEntity<Void>> get(HandleMessageCommandEntity requestParameter, DefaultMcpStreamableMessageFactory.DynamicContext dynamicContext) throws Exception {
        return sessionNode;
    }

}
