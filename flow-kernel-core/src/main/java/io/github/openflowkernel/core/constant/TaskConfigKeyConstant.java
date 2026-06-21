package io.github.openflowkernel.core.constant;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TaskConfigKeyConstant {
    public static final Set<String> TASK_CONFIG_KEYS = new LinkedHashSet<>();

    public static final String HANDLER_NAME = "HANDLER_NAME";
    public static final String TASK_NAME = "TASK_NAME";
    public static final String NO = "NO";
    public static final String TASK_PERMISSION = "TASK_PERMISSION";
    public static final String TASK_BEGIN_NOTIFY = "TASK_BEGIN_NOTIFY";
    public static final String TASK_END_NOTIFY = "TASK_END_NOTIFY";
    public static final String TASK_DESCRIPTION = "TASK_DESCRIPTION";
    public static final String INPUT = "INPUT";
    public static final String OUTPUT = "OUTPUT";

    public static final String FORM_DEF_ID = "FORM_DEF_ID";
    public static final String FORM_CODE = "FORM_CODE";
    public static final String FORM_DEF_MAP = "FORM_DEF_MAP";
    public static final String FORM_DETAIL_DEF_MAP = "FORM_DETAIL_DEF_MAP";
    public static final String FORM_DEF_HEADER_MAP = "FORM_DEF_HEADER_MAP";
    public static final String FORM_DEF_INDEX = "FORM_DEF_INDEX";
    public static final String FORM_DETAIL_DEF_INDEX = "FORM_DETAIL_DEF_INDEX";
    public static final String FORM_INPUT = "FORM_INPUT";
    public static final String FORM_OUTPUT = "FORM_OUTPUT";
    public static final String FORM_INPUT_MAP = "FORM_INPUT_MAP";
    public static final String FORM_OUTPUT_MAP = "FORM_OUTPUT_MAP";

    public static final String SUB_PROCESS_DEF_CODE = "SUB_PROCESS_DEF_CODE";
    public static final String SUB_PROCESS_DEF_CODE_MAP = "SUB_PROCESS_DEF_CODE_MAP";
    public static final String ACTION_DEF = "ACTION_DEF";
    public static final String PIPE_CODE = "PIPE_CODE";
    public static final String TASK_TYPE = "TASK_TYPE";
    public static final String GROUP_NAME = "GROUP_NAME";
    public static final String TASK_CANDIDATE = "CANDIDATE_SELECTION";
    public static final String TASK_FORCE_CANDIDATE = "TASK_FORCE_CANDIDATE";
    public static final String TASK_CANDIDATE_ROLE_TYPES = "TASK_CANDIDATE_ROLE_TYPES";

    static {
        TASK_CONFIG_KEYS.add(HANDLER_NAME);
        TASK_CONFIG_KEYS.add(TASK_NAME);
        TASK_CONFIG_KEYS.add(GROUP_NAME);
        TASK_CONFIG_KEYS.add(TASK_TYPE);
        TASK_CONFIG_KEYS.add(SUB_PROCESS_DEF_CODE);
        TASK_CONFIG_KEYS.add(ACTION_DEF);
        TASK_CONFIG_KEYS.add(FORM_DEF_ID);
        TASK_CONFIG_KEYS.add(FORM_INPUT);
        TASK_CONFIG_KEYS.add(FORM_OUTPUT);
        TASK_CONFIG_KEYS.add(INPUT);
        TASK_CONFIG_KEYS.add(NO);
        TASK_CONFIG_KEYS.add(OUTPUT);
        TASK_CONFIG_KEYS.add(SUB_PROCESS_DEF_CODE_MAP);
        TASK_CONFIG_KEYS.add(TASK_BEGIN_NOTIFY);
        TASK_CONFIG_KEYS.add(TASK_DESCRIPTION);
        TASK_CONFIG_KEYS.add(TASK_END_NOTIFY);
        TASK_CONFIG_KEYS.add(TASK_CANDIDATE);
        TASK_CONFIG_KEYS.add(TASK_CANDIDATE_ROLE_TYPES);
    }

    private TaskConfigKeyConstant() {
    }
}
