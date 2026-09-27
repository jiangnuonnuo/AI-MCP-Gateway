package cn.bugstack.ai.test.domain.llm;

import cn.bugstack.ai.domain.llm.service.impl.LLMService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LLMServiceEndpointTest {

    @Test
    void appendsEncodedApiKeyToBothMcpEndpointForms() throws Exception {
        Method method = LLMService.class.getDeclaredMethod("withApiKey", String.class, String.class);
        method.setAccessible(true);

        assertEquals("/gateway/mcp?api_key=key-123", method.invoke(null, "/gateway/mcp", "key-123"));
        assertEquals("/gateway/sse?session=1&api_key=Bearer+key%2F123", method.invoke(null,
                "/gateway/sse?session=1", "Bearer key/123"));
        assertEquals("/gateway/mcp", method.invoke(null, "/gateway/mcp", " "));
    }
}
