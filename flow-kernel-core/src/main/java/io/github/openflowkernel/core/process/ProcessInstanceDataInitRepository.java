package io.github.openflowkernel.core.process;

import java.util.List;
import java.util.Map;

public interface ProcessInstanceDataInitRepository {
    void init(long processDefinitionId, List<String> cityCodes, String key, String value);

    Map<String, String> get(long processDefinitionId, String cityCode);

    void delete(long processDefinitionId, List<String> cityCodes, List<String> keys);

    void update(long processDefinitionId, List<String> cityCodes, String key, String value);
}
