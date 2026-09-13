package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.infrastructure.adapter.port.HttpToolExecutor;
import cn.bugstack.ai.infrastructure.gateway.GenericHttpGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpToolExecutorTest {

    @Test
    void postKeepsMultipleTopLevelArguments() throws Exception {
        GenericHttpGateway gateway = mock(GenericHttpGateway.class);
        Call<ResponseBody> call = mock(Call.class);
        when(gateway.post(eq("https://example.test/tool"), any(), any())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(ResponseBody.create("ok", null)));

        McpToolProtocolConfigVO.HTTPConfig http = new McpToolProtocolConfigVO.HTTPConfig();
        http.setHttpUrl("https://example.test/tool");
        http.setHttpMethod("POST");
        HttpToolExecutor executor = new HttpToolExecutor();
        ReflectionTestUtils.setField(executor, "gateway", gateway);
        ReflectionTestUtils.setField(executor, "objectMapper", new ObjectMapper());
        ToolExecutionResult result = executor.execute(ToolExecutionContext.http(
                "r-1", "g-1", "tool", McpToolProtocolConfigVO.builder().httpConfig(http).build(),
                new LinkedHashMap<>(Map.of("first", 1, "second", Map.of("nested", true)))));

        assertTrue(result.isSuccess());
        verify(gateway).post(eq("https://example.test/tool"), any(), any());
    }

    @Test
    void getFlattensIndependentAndNestedArguments() throws Exception {
        GenericHttpGateway gateway = mock(GenericHttpGateway.class);
        Call<ResponseBody> call = mock(Call.class);
        when(gateway.get(eq("https://example.test/1"), any(), any())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(ResponseBody.create("ok", null)));

        McpToolProtocolConfigVO.HTTPConfig http = new McpToolProtocolConfigVO.HTTPConfig();
        http.setHttpUrl("https://example.test/{id}");
        http.setHttpMethod("GET");
        HttpToolExecutor executor = new HttpToolExecutor();
        ReflectionTestUtils.setField(executor, "gateway", gateway);
        ReflectionTestUtils.setField(executor, "objectMapper", new ObjectMapper());
        ToolExecutionResult result = executor.execute(ToolExecutionContext.http(
                "r-2", "g-1", "tool", McpToolProtocolConfigVO.builder().httpConfig(http).build(),
                Map.of("id", 1, "query", "sales", "filters", Map.of("region", "north"))));

        assertTrue(result.isSuccess());
        verify(gateway).get(eq("https://example.test/1"), any(), any());
    }
}
