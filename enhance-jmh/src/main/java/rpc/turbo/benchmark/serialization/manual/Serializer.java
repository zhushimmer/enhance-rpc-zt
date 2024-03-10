package rpc.turbo.benchmark.serialization.manual;

import io.netty.buffer.ByteBuf;

public interface Serializer<T> {

	public void write(ByteBuf byteBuf, T object);

	public T read(ByteBuf byteBuf);

	public Class<? super T> typeClass();
}
