package io.github.openflowkernel.core.candidate;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface TaskCandidateSource {
    List<TaskCandidate> select(
        String selectionConfig,
        Map<String, String> processInstData
    );
}
