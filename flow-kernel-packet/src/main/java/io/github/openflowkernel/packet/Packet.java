package io.github.openflowkernel.packet;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public abstract class Packet<V> {
    private Long procInstId;
    private Long procTaskInstId;
    private String taskCode;
    private V value;
    private Map<String, PacketSource> source = Map.of();

    protected Packet() {
    }

    public final void load(PacketValueRecord valueRecord) {
        Objects.requireNonNull(valueRecord, "valueRecord");
        this.procInstId = valueRecord.processInstanceId();
        this.procTaskInstId = valueRecord.processTaskInstanceId();
        this.taskCode = valueRecord.taskCode();
        this.value = deserializeValue(firstNonBlank(
            valueRecord.value(),
            valueRecord.initValue()
        ));
        this.source = deserializeSource(firstNonBlank(
            valueRecord.source(),
            valueRecord.initSource()
        ));
    }

    public final PacketValueRecord toValueRecord() {
        return new PacketValueRecord(
            0,
            procInstId == null ? 0 : procInstId,
            procTaskInstId == null ? 0 : procTaskInstId,
            taskCode == null ? "" : taskCode,
            "",
            getValueStr(),
            "",
            getSourceStr(),
            PacketValueStatusEnum.COMPLETED,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    public final String getValueStr() {
        return serializeValue(value);
    }

    public final String getSourceStr() {
        return serializeSource(source);
    }

    protected abstract V deserializeValue(String value);

    protected abstract String serializeValue(V value);

    protected Map<String, PacketSource> deserializeSource(String sourceValue) {
        return Map.of();
    }

    protected String serializeSource(Map<String, PacketSource> sourceValue) {
        return "{}";
    }

    public abstract String getCurrentProcIntroduction();

    public abstract String getCurrentProcName();

    public Long getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(Long procInstId) {
        this.procInstId = procInstId;
    }

    public Long getProcTaskInstId() {
        return procTaskInstId;
    }

    public void setProcTaskInstId(Long procTaskInstId) {
        this.procTaskInstId = procTaskInstId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public Map<String, PacketSource> getSource() {
        return source;
    }

    public void setSource(Map<String, PacketSource> source) {
        this.source = source == null ? Map.of() : Map.copyOf(source);
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "{}" : fallback;
    }
}
