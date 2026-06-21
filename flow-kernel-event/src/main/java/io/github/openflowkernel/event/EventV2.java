package io.github.openflowkernel.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class EventV2 implements DomainEvent {
    private static final Map<Class<? extends EventV2>, String> EVENT_NAME_MAP =
        new HashMap<>();

    private BasicInfo basicInfo;

    protected EventV2() {
        this.basicInfo = new BasicInfo();
    }

    public static Class<? extends EventV2> getEventClass(String name) {
        return EVENT_NAME_MAP.entrySet().stream()
            .filter(entry -> entry.getValue().equals(name))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    public static synchronized void initRegister() {
        EVENT_NAME_MAP.clear();
    }

    public static synchronized void registerName(Class<? extends EventV2> clazz, String name) {
        EVENT_NAME_MAP.put(clazz, name);
    }

    public static Map<Class<? extends EventV2>, String> getEventNameMap() {
        return Collections.unmodifiableMap(EVENT_NAME_MAP);
    }

    public final String getEventName() {
        return EVENT_NAME_MAP.get(getClass());
    }

    @Override
    public final String eventType() {
        String eventName = getEventName();
        return eventName == null ? getClass().getName() : eventName;
    }

    public BasicInfo getBasicInfo() {
        return basicInfo;
    }

    public void setBasicInfo(BasicInfo basicInfo) {
        this.basicInfo = basicInfo;
    }

    public static class BasicInfo {
        private Long eventId;
        private String eventGroup = "DEFAULT";
        private String env;
        private String sourceEnv;
        private String contextKeyType;
        private String contextKey;

        public Long getEventId() {
            return eventId;
        }

        public void setEventId(Long eventId) {
            this.eventId = eventId;
        }

        public String getEventGroup() {
            return eventGroup;
        }

        public void setEventGroup(String eventGroup) {
            this.eventGroup = eventGroup;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public String getSourceEnv() {
            return sourceEnv;
        }

        public void setSourceEnv(String sourceEnv) {
            this.sourceEnv = sourceEnv;
        }

        public String getContextKeyType() {
            return contextKeyType;
        }

        public void setContextKeyType(String contextKeyType) {
            this.contextKeyType = contextKeyType;
        }

        public String getContextKey() {
            return contextKey;
        }

        public void setContextKey(String contextKey) {
            this.contextKey = contextKey;
        }
    }
}
