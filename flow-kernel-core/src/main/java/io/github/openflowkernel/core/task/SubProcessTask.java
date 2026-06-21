package io.github.openflowkernel.core.task;

import io.github.openflowkernel.core.constant.ProcessInstDataKeyConstant;
import io.github.openflowkernel.core.constant.RelationConstant;
import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.constant.TaskInstDataKeyConstant;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.core.support.FlowExpressionEvaluator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubProcessTask extends ExternalTask {
    private static final String SUB_PROCESS_INST_ID = "subProcessInstId";
    private static final String SUB_PROCESS_INST_MAP = "subProcessInstMap";

    protected SubProcessTask(TaskRelationService taskRelationService) {
        super(taskRelationService);
    }

    @Override
    public TaskResult init(TaskContext context) {
        Map<String, String> definitionMap = subProcessDefinitionMap(context);
        Map<String, String> input = buildInput(context);
        if (definitionMap.isEmpty()) {
            long subProcessInstanceId = taskInstance().createSubProcess(
                subProcessDefinitionKey(context),
                input
            );
            return TaskResult.builder()
                .taskData(SUB_PROCESS_INST_ID, Long.toString(subProcessInstanceId))
                .build();
        }
        Map<String, String> subProcessInstMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definitionMap.entrySet()) {
            long subProcessInstanceId = taskInstance().createSubProcess(
                entry.getValue(),
                input
            );
            subProcessInstMap.put(entry.getKey(), Long.toString(subProcessInstanceId));
        }
        return TaskResult.builder()
            .taskData(SUB_PROCESS_INST_MAP, toFlatObject(subProcessInstMap))
            .build();
    }

    public TaskResult outputData() {
        TaskContext context = taskInstance().context();
        List<ProcessInstance> subProcesses = taskInstance()
            .findSubProcesses();
        if (subProcesses.size() != 1) {
            return TaskResult.empty();
        }
        ProcessInstance subProcess = subProcesses.get(0);
        Map<String, String> output = buildOutput(context, subProcess);
        copyPreviewUrls(subProcess, output);
        if (output.isEmpty()) {
            return TaskResult.empty();
        }
        return new TaskResult(output, Map.of());
    }

    protected String subProcessDefinitionKey(TaskContext context) {
        String value = context.getTaskConfig().get(TaskConfigKeyConstant.SUB_PROCESS_DEF_CODE);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Task config missing " + TaskConfigKeyConstant.SUB_PROCESS_DEF_CODE
            );
        }
        return value;
    }

    protected Map<String, String> subProcessDefinitionMap(TaskContext context) {
        String value = context.getTaskConfig().get(
            TaskConfigKeyConstant.SUB_PROCESS_DEF_CODE_MAP
        );
        return parseFlatObject(value);
    }

    protected Map<String, String> buildInput(TaskContext context) {
        String formInput = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_INPUT);
        Map<String, String> result = new LinkedHashMap<>();
        if (formInput != null && !formInput.isBlank()) {
            result.putAll(FlowExpressionEvaluator.evaluateMap(
                formInput,
                context.getProcessInstData(),
                context.getCurrentTaskInstData(),
                Map.of()
            ));
        }
        result.putIfAbsent(
            ProcessInstDataKeyConstant.PROC_TYPE,
            ProcessInstDataKeyConstant.PROC_TYPE_RELATE
        );
        Long processInstId = context.getProcessInstId();
        if (processInstId != null) {
            result.putIfAbsent(
                ProcessInstDataKeyConstant.MAIN_PROC_INST_ID,
                Long.toString(processInstId)
            );
        }
        return result;
    }

    protected Map<String, String> buildOutput(
        TaskContext context,
        ProcessInstance subProcess
    ) {
        String formOutput = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_OUTPUT);
        if (formOutput == null || formOutput.isBlank()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(FlowExpressionEvaluator.evaluateMap(
            formOutput,
            context.getProcessInstData(),
            context.getCurrentTaskInstData(),
            subProcess.data()
        ));
    }

    private static void copyPreviewUrls(
        ProcessInstance subProcess,
        Map<String, String> output
    ) {
        for (Map.Entry<String, String> entry : subProcess.data().entrySet()) {
            if (entry.getKey().startsWith(TaskInstDataKeyConstant.PREVIEW_URL_PREFIX)) {
                output.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static String relationType() {
        return RelationConstant.SUB_PROCESS_TASK_RELATION_TYPE;
    }

    private static Map<String, String> parseFlatObject(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        String body = value.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        Map<String, String> result = new LinkedHashMap<>();
        int index = 0;
        while (index < body.length()) {
            index = skipWhitespaceAndComma(body, index);
            if (index >= body.length()) {
                break;
            }
            ParsedToken key = readToken(body, index);
            index = skipWhitespace(body, key.nextIndex());
            if (index >= body.length() || body.charAt(index) != ':') {
                throw new IllegalArgumentException("Invalid map expression: " + value);
            }
            index = skipWhitespace(body, index + 1);
            ParsedToken parsedValue = readToken(body, index);
            result.put(key.value(), parsedValue.value());
            index = parsedValue.nextIndex();
        }
        return result;
    }

    private static String toFlatObject(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append('"')
                .append(':')
                .append('"').append(escape(entry.getValue())).append('"');
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int skipWhitespaceAndComma(String value, int index) {
        int current = index;
        while (current < value.length()) {
            char c = value.charAt(current);
            if (!Character.isWhitespace(c) && c != ',') {
                return current;
            }
            current++;
        }
        return current;
    }

    private static int skipWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private static ParsedToken readToken(String value, int index) {
        if (index < value.length() && value.charAt(index) == '"') {
            return readQuoted(value, index + 1);
        }
        int current = index;
        while (current < value.length()
            && value.charAt(current) != ','
            && value.charAt(current) != ':') {
            current++;
        }
        return new ParsedToken(value.substring(index, current).trim(), current);
    }

    private static ParsedToken readQuoted(String value, int index) {
        StringBuilder result = new StringBuilder();
        int current = index;
        while (current < value.length()) {
            char c = value.charAt(current);
            if (c == '\\' && current + 1 < value.length()) {
                result.append(value.charAt(current + 1));
                current += 2;
                continue;
            }
            if (c == '"') {
                return new ParsedToken(result.toString(), current + 1);
            }
            result.append(c);
            current++;
        }
        throw new IllegalArgumentException("Unclosed quoted string");
    }

    private record ParsedToken(String value, int nextIndex) {
    }
}
