package io.github.openflowkernel.packet;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PacketFactory {
    private final Map<String, Supplier<? extends Packet<?>>> packetClassMap =
        new LinkedHashMap<>();

    public void registerPacketClass(
        String className,
        Supplier<? extends Packet<?>> supplier
    ) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(supplier, "supplier");
        if (packetClassMap.putIfAbsent(className, supplier) != null) {
            throw new IllegalArgumentException("Packet class already registered: " + className);
        }
    }

    public void registerPacketClass(
        String className,
        Class<? extends Packet<?>> type
    ) {
        registerPacketClass(className, () -> instantiateType(type));
    }

    public Optional<Class<?>> getPacketClass(String className) {
        return Optional.ofNullable(packetClassMap.get(className))
            .map(Supplier::get)
            .map(Object::getClass);
    }

    @SuppressWarnings("unchecked")
    public <P extends Packet<?>> P instantiate(
        String packetResolverClass,
        PacketValueRecord packetValueRecord
    ) {
        Packet<?> packet = instantiate(packetResolverClass);
        packet.load(packetValueRecord);
        return (P) packet;
    }

    @SuppressWarnings("unchecked")
    public <P extends Packet<?>> P instantiate(
        String packetResolverClass,
        long processInstanceId,
        long processTaskInstanceId,
        String taskCode
    ) {
        Packet<?> packet = instantiate(packetResolverClass);
        packet.setProcInstId(processInstanceId);
        packet.setProcTaskInstId(processTaskInstanceId);
        packet.setTaskCode(taskCode);
        return (P) packet;
    }

    private Packet<?> instantiate(String packetResolverClass) {
        Supplier<? extends Packet<?>> supplier = packetClassMap.get(packetResolverClass);
        if (supplier == null) {
            throw new IllegalArgumentException(
                "Packet class not registered: " + packetResolverClass
            );
        }
        return supplier.get();
    }

    private static Packet<?> instantiateType(Class<? extends Packet<?>> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException
                 | NoSuchMethodException exception) {
            throw new IllegalStateException("Instantiate Packet Error: " + type, exception);
        }
    }
}
