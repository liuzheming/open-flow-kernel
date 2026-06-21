package io.github.openflowkernel.core.candidate;

import java.util.Objects;

public class TaskCandidate {
    private String ucid;
    private String userCode;
    private String username;
    private Integer status;
    private String orgName;

    public TaskCandidate() {
    }

    public TaskCandidate(String ucid, String userCode, String username) {
        this(ucid, userCode, username, null);
    }

    public TaskCandidate(
        String ucid,
        String userCode,
        String username,
        Integer status
    ) {
        this.ucid = ucid;
        this.userCode = userCode;
        this.username = username;
        this.status = status;
    }

    public String getUcid() {
        return ucid;
    }

    public void setUcid(String ucid) {
        this.ucid = ucid;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        TaskCandidate that = (TaskCandidate) other;
        return Objects.equals(ucid, that.ucid)
            && Objects.equals(userCode, that.userCode)
            && Objects.equals(username, that.username)
            && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ucid, userCode, username, status);
    }
}
