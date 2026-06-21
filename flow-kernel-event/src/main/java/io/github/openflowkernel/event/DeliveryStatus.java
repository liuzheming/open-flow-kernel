package io.github.openflowkernel.event;

public enum DeliveryStatus {
    PENDING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED_PERMANENTLY
}
