 package io.netty.handler.codec.haproxy;

 public enum ProtocolDetectionState {
   NEEDS_MORE_DATA,
   INVALID,
   DETECTED;
 }
