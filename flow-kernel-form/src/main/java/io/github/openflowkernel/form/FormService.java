package io.github.openflowkernel.form;

import java.util.Map;

public interface FormService {
    void setSubmissionListener(FormSubmissionListener listener);

    long create(String definitionKey, Map<String, String> initialData);

    FormInstance get(long formInstanceId);

    void submit(long formInstanceId, Map<String, String> submittedData);

    void cancel(long formInstanceId);
}
