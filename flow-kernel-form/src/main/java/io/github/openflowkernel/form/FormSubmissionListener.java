package io.github.openflowkernel.form;

@FunctionalInterface
public interface FormSubmissionListener {
    void onSubmitted(long formInstanceId);
}
