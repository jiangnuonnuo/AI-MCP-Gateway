# Real acceptance evidence

## Environment

- Application: `http://127.0.0.1:8779/api-gateway`
- Control database: `ai_mcp_gateway_v2`
- MySQL datasource: `data-warehouse` (restored to enabled)
- Acceptance gateway: `gateway_acceptance_20260925`
- Acceptance binding: `id=43`, `queryDataWarehouseOrderSummary`, `protocolId=900002`, restored to enabled
- Template: `protocolId=900002`, version `1`
- Fixture window: `2024-01-01 00:00:00` inclusive to `2025-01-01 00:00:00` exclusive

## Results

| Layer | Result | Observed evidence |
| --- | --- | --- |
| Template preflight API | PASS | Five stages succeeded; 8 rows, 4 columns, `truncated=false` |
| Tool manual API | PASS | `testId`, `requestId`, `queryId`, five stages and raw JSON returned; 8 rows |
| MCP SSE | PASS | `initialize`, `tools/list`, `tools/call` returned through the SSE message channel |
| MCP Streamable HTTP | PASS | `initialize`, `tools/list`, `tools/call` returned with `Mcp-Session-Id` |
| Agent SSE | PASS | Real trace contained `AGENT_STARTED`, `TOOLS_LIST_RESPONSE`, `TOOL_CALL_REQUEST`, `TOOL_CALL_RESPONSE`, `AGENT_FINISHED` |
| Agent Streamable HTTP | PASS | Same five-event trace and final answer based on Tool output |
| Agent no-tool | PASS | Only start, tools-list and finish events; no Tool call event |
| Browser template mode | PASS | Real template/version/data-source selection, parameter filling and raw response JSON |
| Browser Tool mode | PASS | Real Gateway/Tool selection, schema-driven parameters, five-stage trace and raw Tool JSON |
| Browser Agent mode | PASS | Natural-language request, transport selection and five-event trace visible |

## Negative checks

- Missing template parameter: `SQL_PARAMETER_ERROR`, failed at `PARAMETER_VALIDATION`.
- Unknown template parameter: `SQL_PARAMETER_ERROR`, failed at `PARAMETER_VALIDATION`.
- Tool type mismatch: `INVALID_ARGUMENT`, failed at `PARAMETER_VALIDATION`.
- Unknown Tool: `TOOL_NOT_FOUND`, failed at `TOOL_RESOLUTION`.
- Disabled Tool: `TOOL_DISABLED`, Tool directory empty; binding restored to enabled.
- Disabled datasource: `DATASOURCE_UNAVAILABLE`; datasource restored to enabled.
- Invalid API key on an authenticated gateway: SSE error `fail to auth apikey`.

## Build and static checks

- `mvn test`: 85 tests, 0 failures, 9 skipped by external-dependency guards.
- `node --check` passed for test-center, MySQL workbench and config scripts.
- `git diff --check` passed.
- `openspec validate mysql-template-test-console-execution --type change --strict` passed.
