package io.netty.handler.codec.haproxy;

public enum HAProxyProtocolVersion {
    V1((byte)16),
    V2((byte)32);

    private final byte byteValue;

    HAProxyProtocolVersion(byte byteValue) {
        this.byteValue = byteValue;
    }

    public byte byteValue() {
        return this.byteValue;
    }
}
