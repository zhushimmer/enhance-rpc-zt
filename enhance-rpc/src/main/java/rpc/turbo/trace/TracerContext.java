package rpc.turbo.trace;

import java.util.concurrent.ThreadLocalRandom;

import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.util.concurrent.AttachmentThreadUtils;
import rpc.turbo.util.uuid.ObjectId;

/**
 * tracer上下文
 * 
 * @author zhushimmer
 *
 * @see Request
 * @see Response
 */
public class TracerContext {
	private static final int ATTACHMENT_INDEX = AttachmentThreadUtils.nextVarIndex();

	/**
	 * 设置tracer上下文，后续请求将会作为parent使用
	 * 
	 * @param tracer
	 */
	public static void setTracer(Tracer tracer) {
		AttachmentThreadUtils.put(ATTACHMENT_INDEX, tracer);
	}

	/**
	 * 如果有parent，会自动设置上
	 * 
	 * @return
	 */
	public static Tracer nextTracer() {
		Tracer tracerContext = AttachmentThreadUtils.get(ATTACHMENT_INDEX);

		Tracer tracer = new Tracer();
		tracer.setSpanId(ThreadLocalRandom.current().nextLong());

		if (tracerContext != null) {
			tracer.setTraceId(tracerContext.getTraceId());
			tracer.setParentId(tracerContext.getSpanId());
		} else {
			tracer.setTraceId(ObjectId.next());
			tracer.setParentId(0);
		}

		return tracer;
	}
}
