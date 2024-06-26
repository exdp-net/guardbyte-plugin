package io.netty.handler.codec.haproxy;

public final class ProtocolDetectionResult<T> {
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private static final ProtocolDetectionResult NEEDS_MORE_DATE = new ProtocolDetectionResult(ProtocolDetectionState.NEEDS_MORE_DATA, null);
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private static final ProtocolDetectionResult INVALID = new ProtocolDetectionResult(ProtocolDetectionState.INVALID, null);
    private final ProtocolDetectionState state;
    private final T result;

    @SuppressWarnings("unchecked")
	public static <T> ProtocolDetectionResult<T> needsMoreData() {
        return NEEDS_MORE_DATE;
    }

    @SuppressWarnings("unchecked")
	public static <T> ProtocolDetectionResult<T> invalid() {
        return INVALID;
    }

    public static <T> ProtocolDetectionResult<T> detected(T protocol) {
        return new ProtocolDetectionResult<T>(ProtocolDetectionState.DETECTED, (T)ObjectUtil.checkNotNull(protocol, "protocol"));
    }
    
    private ProtocolDetectionResult(ProtocolDetectionState state, T result) {
        this.state = state;
        this.result = result;
    }

    public ProtocolDetectionState state() {
        return this.state;
    }

    public T detectedProtocol() {
        return this.result;
    }
}
