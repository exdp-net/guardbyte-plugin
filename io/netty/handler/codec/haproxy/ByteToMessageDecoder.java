package io.netty.handler.codec.haproxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.internal.RecyclableArrayList;
import io.netty.util.internal.StringUtil;
import java.util.List;

public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {
  public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
    public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
      ByteBuf buffer;
      if (cumulation.writerIndex() > cumulation.maxCapacity() - in.readableBytes() || cumulation.refCnt() > 1) {
        buffer = ByteToMessageDecoder.expandCumulation(alloc, cumulation, in.readableBytes());
      } else {
        buffer = cumulation;
      } 
      buffer.writeBytes(in);
      in.release();
      return buffer;
    }
  };

  public static final Cumulator COMPOSITE_CUMULATOR = new Cumulator() {
    public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
      CompositeByteBuf compositeByteBuf = null;
      if (cumulation.refCnt() > 1) {
        ByteBuf buffer = ByteToMessageDecoder.expandCumulation(alloc, cumulation, in.readableBytes());
        buffer.writeBytes(in);
        in.release();
      } else {
        CompositeByteBuf composite;
        if (cumulation instanceof CompositeByteBuf) {
          composite = (CompositeByteBuf)cumulation;
        } else {
          int readable = cumulation.readableBytes();
          composite = alloc.compositeBuffer(2147483647);
          composite.addComponent(cumulation).writerIndex(readable);
        } 
        composite.addComponent(in).writerIndex(composite.writerIndex() + in.readableBytes());
        compositeByteBuf = composite;
      } 
      return (ByteBuf)compositeByteBuf;
    }
  };
  
  ByteBuf cumulation;
  private Cumulator cumulator = MERGE_CUMULATOR;
  private boolean singleDecode;
  private boolean decodeWasNull;
  private boolean first;
  private int discardAfterReads = 16;
  private int numReads;
  
  protected ByteToMessageDecoder() {
    CodecUtil.ensureNotSharable((ChannelHandlerAdapter)this);
  }

  public void setSingleDecode(boolean singleDecode) {
    this.singleDecode = singleDecode;
  }

  public boolean isSingleDecode() {
    return this.singleDecode;
  }

  public void setCumulator(Cumulator cumulator) {
    if (cumulator == null) {
      throw new NullPointerException("cumulator");
    }
    this.cumulator = cumulator;
  }

  public void setDiscardAfterReads(int discardAfterReads) {
    if (discardAfterReads <= 0) {
      throw new IllegalArgumentException("discardAfterReads must be > 0");
    }
    this.discardAfterReads = discardAfterReads;
  }

  protected int actualReadableBytes() {
    return internalBuffer().readableBytes();
  }

  protected ByteBuf internalBuffer() {
    if (this.cumulation != null) {
      return this.cumulation;
    }
    return Unpooled.EMPTY_BUFFER;
  }

  public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    ByteBuf buf = internalBuffer();
    int readable = buf.readableBytes();
    if (readable > 0) {
      ByteBuf bytes = buf.readBytes(readable);
      buf.release();
      ctx.fireChannelRead(bytes);
    } else {
      buf.release();
    }
    this.cumulation = null;
    this.numReads = 0;
    ctx.fireChannelReadComplete();
    handlerRemoved0(ctx);
  }

  protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {}

  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof ByteBuf) {
      RecyclableArrayList out = RecyclableArrayList.newInstance();
      try {
        ByteBuf data = (ByteBuf)msg;
        this.first = (this.cumulation == null);
        if (this.first) {
          this.cumulation = data;
        } else {
          this.cumulation = this.cumulator.cumulate(ctx.alloc(), this.cumulation, data);
        }
        callDecode(ctx, this.cumulation, (List<Object>)out);
      } catch (DecoderException e) {
        throw e;
      } catch (Throwable t) {
        throw new DecoderException(t);
      } finally {
        if (this.cumulation != null && !this.cumulation.isReadable()) {
          this.numReads = 0;
          this.cumulation.release();
          this.cumulation = null;
        } else if (++this.numReads >= this.discardAfterReads) {
          this.numReads = 0;
          discardSomeReadBytes();
        }
        int size = out.size();
        fireChannelRead(ctx, (List<Object>)out, size);
        out.recycle();
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }

  static void fireChannelRead(ChannelHandlerContext ctx, List<Object> msgs, int numElements) {
    for (int i = 0; i < numElements; i++) {
      ctx.fireChannelRead(msgs.get(i));
    }
  }

  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    this.numReads = 0;
    discardSomeReadBytes();
    if (this.decodeWasNull) {
      this.decodeWasNull = false;
      if (!ctx.channel().config().isAutoRead()) {
        ctx.read();
      }
    }
    ctx.fireChannelReadComplete();
  }
  
  protected final void discardSomeReadBytes() {
    if (this.cumulation != null && !this.first && this.cumulation.refCnt() == 1) {
      this.cumulation.discardSomeReadBytes();
    }
  }

  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    channelInputClosed(ctx, true);
  }

  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof io.netty.channel.socket.ChannelInputShutdownEvent)
      channelInputClosed(ctx, false); 
    super.userEventTriggered(ctx, evt);
  }
  
  private void channelInputClosed(ChannelHandlerContext ctx, boolean callChannelInactive) throws Exception {
    RecyclableArrayList out = RecyclableArrayList.newInstance();
    try {
      if (this.cumulation != null) {
        callDecode(ctx, this.cumulation, (List<Object>)out);
        decodeLast(ctx, this.cumulation, (List<Object>)out);
      } else {
        decodeLast(ctx, Unpooled.EMPTY_BUFFER, (List<Object>)out);
      } 
    } catch (DecoderException e) {
      throw e;
    } catch (Exception e) {
      throw new DecoderException(e);
    } finally {
      try {
        if (this.cumulation != null) {
          this.cumulation.release();
          this.cumulation = null;
        } 
        int size = out.size();
        fireChannelRead(ctx, (List<Object>)out, size);
        if (size > 0)
          ctx.fireChannelReadComplete(); 
        if (callChannelInactive)
          ctx.fireChannelInactive(); 
      } finally {
        out.recycle();
      } 
    } 
  }

  protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    try {
      while (in.isReadable()) {
        int outSize = out.size();
        if (outSize > 0) {
          fireChannelRead(ctx, out, outSize);
          out.clear();
          if (ctx.isRemoved())
            break; 
          outSize = 0;
        } 
        int oldInputLength = in.readableBytes();
        decode(ctx, in, out);
        if (ctx.isRemoved())
          break; 
        if (outSize == out.size()) {
          if (oldInputLength == in.readableBytes())
            break; 
          continue;
        } 
        if (oldInputLength == in.readableBytes())
          throw new DecoderException(StringUtil.simpleClassName(getClass()) + ".decode() did not read anything but decoded a message."); 
        if (isSingleDecode())
          break; 
      } 
    } catch (DecoderException e) {
      throw e;
    } catch (Throwable cause) {
      throw new DecoderException(cause);
    } 
  }

  protected abstract void decode(ChannelHandlerContext paramChannelHandlerContext, ByteBuf paramByteBuf, List<Object> paramList) throws Exception;

  protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    decode(ctx, in, out);
  }

  static ByteBuf expandCumulation(ByteBufAllocator alloc, ByteBuf cumulation, int readable) {
    ByteBuf oldCumulation = cumulation;
    cumulation = alloc.buffer(oldCumulation.readableBytes() + readable);
    cumulation.writeBytes(oldCumulation);
    oldCumulation.release();
    return cumulation;
  }

  public static interface Cumulator {
    ByteBuf cumulate(ByteBufAllocator param1ByteBufAllocator, ByteBuf param1ByteBuf1, ByteBuf param1ByteBuf2);
  }
}
