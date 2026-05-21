package cn.bugstack.ai.cases.mcp.streamable.message;

import cn.bugstack.ai.cases.mcp.IMcpMessageService;
import cn.bugstack.ai.domain.session.model.entity.HandleMessageCommandEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 会话消息处理
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/5/19 08:28
 */
@Slf4j
@Service
public class McpStreamableMessageService implements IMcpMessageService {

    @Override
    public ResponseEntity<Void> handleMessage(HandleMessageCommandEntity commandEntity) throws Exception {
       return null;
    }

}
