package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.tool.executor.ToolExecutor;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.infrastructure.gateway.GenericHttpGateway;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 现有 HTTP Tool 的执行策略。HTTP 技术细节集中在 Infrastructure，Domain Handler 不直接依赖 Retrofit。
 */
@Component("httpToolExecutor")
public class HttpToolExecutor implements ToolExecutor {

    private static final Pattern URL_PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private final GenericHttpGateway gateway;
    private final ObjectMapper objectMapper;

    public HttpToolExecutor(GenericHttpGateway gateway) {
        this(gateway, new ObjectMapper());
    }

    public HttpToolExecutor(GenericHttpGateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolBackendType backendType() {
        return ToolBackendType.HTTP;
    }

    @Override
    public ToolExecutionMode executionMode() {
        return ToolExecutionMode.HTTP_REQUEST;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        if (context == null || context.getProtocolConfig() == null
                || context.getProtocolConfig().getHttpConfig() == null) {
            return ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.MISSING_CONFIGURATION,
                    "HTTP tool configuration is missing");
        }

        Map<String, Object> arguments;
        try {
            arguments = context.argumentsAsMap();
        } catch (IllegalArgumentException e) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Tool arguments must be an object");
        }

        McpToolProtocolConfigVO.HTTPConfig config = context.getProtocolConfig().getHttpConfig();
        String method = config.getHttpMethod() == null ? "" : config.getHttpMethod().trim().toLowerCase();
        if (config.getHttpUrl() == null || config.getHttpUrl().isBlank()) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.MISSING_CONFIGURATION,
                    "HTTP tool URL is missing");
        }

        try {
            Map<String, Object> headers = parseHeaders(config.getHttpHeaders());
            if ("post".equals(method)) {
                return executePost(context, config, headers, arguments);
            }
            if ("get".equals(method)) {
                return executeGet(context, config, headers, arguments);
            }
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Unsupported HTTP method");
        } catch (IOException e) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_UNAVAILABLE,
                    "HTTP backend is unavailable");
        } catch (RuntimeException e) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_ERROR,
                    "HTTP backend execution failed");
        }
    }

    private ToolExecutionResult executePost(ToolExecutionContext context,
                                             McpToolProtocolConfigVO.HTTPConfig config,
                                             Map<String, Object> headers,
                                             Map<String, Object> arguments) throws IOException {
        Object payload = arguments.size() == 1 ? arguments.values().iterator().next() : arguments;
        RequestBody requestBody = RequestBody.create(JSON.toJSONString(payload),
                MediaType.parse("application/json"));
        Response<ResponseBody> response = gateway.post(config.getHttpUrl(), headers, requestBody).execute();
        return toResult(context, response);
    }

    private ToolExecutionResult executeGet(ToolExecutionContext context,
                                            McpToolProtocolConfigVO.HTTPConfig config,
                                            Map<String, Object> headers,
                                            Map<String, Object> arguments) throws IOException {
        Map<String, Object> queryParams = flattenArguments(arguments);
        String url = config.getHttpUrl();
        Matcher matcher = URL_PLACEHOLDER.matcher(url);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = queryParams.remove(placeholder);
            if (value == null) {
                value = findNestedValue(arguments, placeholder);
                queryParams.remove(placeholder);
            }
            if (value != null) {
                url = url.replace("{" + placeholder + "}", String.valueOf(value));
            }
        }
        Response<ResponseBody> response = gateway.get(url, headers, queryParams).execute();
        return toResult(context, response);
    }

    private ToolExecutionResult toResult(ToolExecutionContext context,
                                         Response<ResponseBody> response) throws IOException {
        if (response == null) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_ERROR,
                    "HTTP backend returned no response");
        }
        if (!response.isSuccessful()) {
            return ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_ERROR,
                    "HTTP backend returned status " + response.code());
        }
        ResponseBody body = response.body();
        String responseText = body == null ? "" : body.string();
        return ToolExecutionResult.success(context.getRequestId(), convertResponse(responseText,
                context.getProtocolConfig().getResponseProtocolMappings()));
    }

    private Map<String, Object> parseHeaders(String headersJson) throws IOException {
        if (headersJson == null || headersJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(headersJson, new TypeReference<>() {
        });
    }

    private Map<String, Object> flattenArguments(Map<String, Object> arguments) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        arguments.forEach((key, value) -> flattenValue(key, value, flattened));
        return flattened;
    }

    @SuppressWarnings("unchecked")
    private void flattenValue(String key, Object value, Map<String, Object> target) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((nestedKey, nestedValue) -> flattenValue(String.valueOf(nestedKey), nestedValue, target));
            return;
        }
        target.put(key, value);
    }

    private Object findNestedValue(Map<String, Object> arguments, String name) {
        for (Object value : arguments.values()) {
            if (value instanceof Map<?, ?> map && map.containsKey(name)) {
                return map.get(name);
            }
        }
        return null;
    }

    /** 响应映射只在后端返回后参与转换，不会混入请求参数或输入 Schema。 */
    private String convertResponse(String responseText,
                                   List<McpToolProtocolConfigVO.ProtocolMapping> mappings) throws IOException {
        if (mappings == null || mappings.isEmpty() || responseText == null || responseText.isBlank()) {
            return responseText == null ? "" : responseText;
        }
        Object source;
        try {
            source = objectMapper.readValue(responseText, Object.class);
        } catch (IOException ignored) {
            return responseText;
        }
        Map<String, Object> target = new LinkedHashMap<>();
        for (McpToolProtocolConfigVO.ProtocolMapping mapping : mappings) {
            if (mapping == null || mapping.getMcpPath() == null || mapping.getMcpPath().isBlank()) continue;
            Object value = readPath(source, mapping.getMcpPath());
            if (value == null && mapping.getFieldName() != null) value = readPath(source, mapping.getFieldName());
            writePath(target, mapping.getMcpPath(), value);
        }
        return objectMapper.writeValueAsString(target);
    }

    private Object readPath(Object source, String path) {
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) return null;
            current = map.get(segment);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void writePath(Map<String, Object> target, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < segments.length - 1; i++) {
            Object child = current.get(segments[i]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(segments[i], child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(segments[segments.length - 1], value);
    }
}
