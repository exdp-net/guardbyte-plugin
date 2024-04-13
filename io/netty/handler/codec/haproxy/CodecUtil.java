package io.netty.handler.codec.haproxy;

import io.netty.channel.ChannelHandlerAdapter;

final class CodecUtil {
    static void ensureNotSharable(ChannelHandlerAdapter handler) {
        if (handler.isSharable())
            throw new IllegalStateException("@Sharable annotation is not allowed");
    }
}
