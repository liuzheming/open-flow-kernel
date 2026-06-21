package io.github.openflowkernel.core.event;

import io.github.openflowkernel.event.EventV2;

public class ProcessEvent extends EventV2 {
    private Long processInstId;
    private String procName;
    private boolean main;
    private String procEventCode;
    private Long relateProcInstId;
    private Long relateTaskInstId;
    private String procSource;

    static {
        EventV2.registerName(ProcessEvent.class, "ProcessEvent");
    }

    public Long getProcessInstId() {
        return processInstId;
    }

    public void setProcessInstId(Long processInstId) {
        this.processInstId = processInstId;
    }

    public String getProcName() {
        return procName;
    }

    public void setProcName(String procName) {
        this.procName = procName;
    }

    public boolean isMain() {
        return main;
    }

    public void setMain(boolean main) {
        this.main = main;
    }

    public String getProcEventCode() {
        return procEventCode;
    }

    public void setProcEventCode(String procEventCode) {
        this.procEventCode = procEventCode;
    }

    public Long getRelateProcInstId() {
        return relateProcInstId;
    }

    public void setRelateProcInstId(Long relateProcInstId) {
        this.relateProcInstId = relateProcInstId;
    }

    public Long getRelateTaskInstId() {
        return relateTaskInstId;
    }

    public void setRelateTaskInstId(Long relateTaskInstId) {
        this.relateTaskInstId = relateTaskInstId;
    }

    public String getProcSource() {
        return procSource;
    }

    public void setProcSource(String procSource) {
        this.procSource = procSource;
    }
}
