package io.github.openflowkernel.form;

import io.github.openflowkernel.core.constant.RelationConstant;
import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.constant.TaskInstDataKeyConstant;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.core.task.ExternalTask;
import io.github.openflowkernel.core.task.TaskContext;
import io.github.openflowkernel.core.task.TaskResult;
import io.github.openflowkernel.core.support.FlowExpressionEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public abstract class FormTask extends ExternalTask {
    public static final String RELATION_TYPE = RelationConstant.RELATION_FORM;
    public static final String FORM_INSTANCE_ID = TaskInstDataKeyConstant.FORM_INST_ID;
    public static final String FORM_INSTANCE_MAP = TaskInstDataKeyConstant.FORM_INST_MAP;
    private static final String INDEX = "index";

    private final FormService formService;
    private final TaskRelationService relationService;

    protected FormTask(FormService formService, TaskRelationService relationService) {
        super(relationService);
        this.formService = Objects.requireNonNull(formService);
        this.relationService = Objects.requireNonNull(relationService);
    }

    @Override
    public final TaskResult init(TaskContext context) {
        Map<String, String> definitions = formDefinitions(context);
        Map<String, String> inputMap = parseFlatObject(
            context.getTaskConfig().get(TaskConfigKeyConstant.FORM_INPUT_MAP)
        );
        Map<String, String> baseInput = buildFormInput(context);
        Map<String, String> formInstances = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            Map<String, String> input = new LinkedHashMap<>(baseInput);
            String formInput = inputMap.get(entry.getKey());
            if (formInput != null && !formInput.isBlank()) {
                input.putAll(evaluateMap(context, formInput, Map.of()));
            }
            long formInstanceId = formService.create(entry.getValue(), input);
            formInstances.put(entry.getKey(), Long.toString(formInstanceId));
            registerExternalRelation(RELATION_TYPE, Long.toString(formInstanceId));
        }
        String indexKey = context.getTaskConfig()
            .getOrDefault(TaskConfigKeyConstant.FORM_DEF_INDEX, INDEX);
        formInstances.put(INDEX, formInstances.getOrDefault(
            indexKey,
            formInstances.values().iterator().next()
        ));

        TaskResult.TaskResultBuilder builder = TaskResult.builder()
            .taskData(FORM_INSTANCE_MAP, toFlatObject(formInstances));
        if (!containsFormDefMap(context)) {
            builder.taskData(FORM_INSTANCE_ID, formInstances.get(INDEX));
        }
        return builder.build();
    }

    @Override
    public final TaskResult beforeComplete(TaskContext context) {
        if (containsFormDefMap(context)) {
            return new TaskResult(buildMultiFormOutput(context), Map.of());
        }
        long formInstanceId = formInstanceId(context);
        FormInstance form = submittedForm(formInstanceId);
        return new TaskResult(buildFormOutput(context, form), Map.of());
    }

    @Override
    public void cancel() {
        TaskContext context = getTaskInstance().context();
        if (containsFormDefMap(context)) {
            formInstanceMap(context).values().stream()
                .distinct()
                .map(Long::parseLong)
                .forEach(formService::cancel);
            super.cancel();
            return;
        }
        String formInstanceId = context.taskData().get(FORM_INSTANCE_ID);
        if (formInstanceId != null) {
            formService.cancel(Long.parseLong(formInstanceId));
        }
        super.cancel();
    }

    protected String formDefinitionKey(TaskContext context) {
        String formDefinitionId = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_DEF_ID);
        if (formDefinitionId != null && !formDefinitionId.isBlank()) {
            return formDefinitionId;
        }
        return formDefinitionKey();
    }

    protected abstract String formDefinitionKey();

    protected Map<String, String> buildFormInput(TaskContext context) {
        String formInput = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_INPUT);
        if (formInput != null && !formInput.isBlank()) {
            return FlowExpressionEvaluator.evaluateMap(
                formInput,
                context.getProcessInstData(),
                context.getCurrentTaskInstData(),
                Map.of()
            );
        }
        return new LinkedHashMap<>();
    }

    protected Map<String, String> buildFormOutput(
        TaskContext context,
        FormInstance form
    ) {
        String formOutput = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_OUTPUT);
        if (formOutput != null && !formOutput.isBlank()) {
            return FlowExpressionEvaluator.evaluateMap(
                formOutput,
                context.getProcessInstData(),
                context.getCurrentTaskInstData(),
                form.submittedData()
            );
        }
        return Map.of();
    }

    protected final long formInstanceId(TaskContext context) {
        String value = context.taskData().get(FORM_INSTANCE_ID);
        if (value == null && containsFormDefMap(context)) {
            value = formInstanceMap(context).get(INDEX);
        }
        if (value == null) {
            throw new IllegalStateException(
                "Task has no form instance: " + context.taskInstanceId()
            );
        }
        return Long.parseLong(value);
    }

    private Map<String, String> formDefinitions(TaskContext context) {
        if (!containsFormDefMap(context)) {
            return Map.of(INDEX, formDefinitionKey(context));
        }
        return parseFlatObject(context.getTaskConfig().get(TaskConfigKeyConstant.FORM_DEF_MAP));
    }

    private Map<String, String> buildMultiFormOutput(TaskContext context) {
        Map<String, String> outputMap = parseFlatObject(
            context.getTaskConfig().get(TaskConfigKeyConstant.FORM_OUTPUT_MAP)
        );
        if (outputMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> definitions = formDefinitions(context);
        Map<String, String> formInstances = formInstanceMap(context);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : outputMap.entrySet()) {
            String formKey = resolveFormKey(entry.getKey(), definitions);
            if (formKey == null || !formInstances.containsKey(formKey)) {
                continue;
            }
            FormInstance form = submittedForm(Long.parseLong(formInstances.get(formKey)));
            result.putAll(evaluateMap(context, entry.getValue(), form.submittedData()));
        }
        return result;
    }

    private FormInstance submittedForm(long formInstanceId) {
        FormInstance form = formService.get(formInstanceId);
        if (form.status() != FormStatus.SUBMITTED) {
            throw new IllegalStateException("Form is not submitted: " + formInstanceId);
        }
        return form;
    }

    private static boolean containsFormDefMap(TaskContext context) {
        String value = context.getTaskConfig().get(TaskConfigKeyConstant.FORM_DEF_MAP);
        return value != null && !value.isBlank();
    }

    private Map<String, String> formInstanceMap(TaskContext context) {
        return parseFlatObject(context.taskData().get(FORM_INSTANCE_MAP));
    }

    private static Map<String, String> evaluateMap(
        TaskContext context,
        String expression,
        Map<String, String> valueData
    ) {
        return FlowExpressionEvaluator.evaluateMap(
            expression,
            context.getProcessInstData(),
            context.getCurrentTaskInstData(),
            valueData
        );
    }

    private static String resolveFormKey(
        String outputKey,
        Map<String, String> definitions
    ) {
        if (definitions.containsKey(outputKey)) {
            return outputKey;
        }
        return definitions.entrySet().stream()
            .filter(entry -> entry.getValue().equals(outputKey))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
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
