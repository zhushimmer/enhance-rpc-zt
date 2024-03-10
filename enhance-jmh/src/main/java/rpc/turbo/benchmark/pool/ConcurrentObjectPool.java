package rpc.turbo.benchmark.pool;

import static rpc.turbo.util.UnsafeUtils.unsafe;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author zhushimmer
 *
 * @param <T>
 */
@SuppressWarnings("unchecked")
public class ConcurrentObjectPool<T> implements Closeable {

	private static final int ABASE;
	private static final int ASHIFT;

	private static final Object BORROWED = new Object();
	private static final WaitStrategy WAIT_STRATEGY = new WaitStrategy();

	private final int size;

	volatile long p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17;
	private final Object[] array;

	private final Object[] closeList;
	volatile long q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15, q16, q17;

	private volatile boolean isClosing = false;
	volatile long r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15, r16, r17;

	public ConcurrentObjectPool(int poolSize, Supplier<T> producer) {
		this.size = poolSize;
		this.closeList = new Object[poolSize];
		this.array = new Object[poolSize];

		for (int i = 0; i < poolSize; i++) {
			T t = producer.get();

			array[i] = t;
			closeList[i] = t;
		}
	}

	public T borrow() {
		if (isClosing) {
			return null;
		}

		for (int i = 0; i < Integer.MAX_VALUE; i++) {
			int random = ThreadLocalRandom.current().nextInt(size);

			for (int j = 0; j < size; j++) {
				long offset = offset((random + j) % size);

				Object obj = unsafe().getObjectVolatile(array, offset);

				if (obj != BORROWED) {
					if (unsafe().compareAndSwapObject(array, offset, obj, BORROWED)) {
						return (T) obj;
					} else {
						break;
					}
				}
			}

			WAIT_STRATEGY.idle(i);
		}

		return null;
	}

	public void release(T t) {
		if (t == null) {
			return;
		}

		for (int i = 0; i < Integer.MAX_VALUE; i++) {
			int random = ThreadLocalRandom.current().nextInt(size);

			for (int j = 0; j < size; j++) {
				long offset = offset((random + j) % size);

				Object obj = unsafe().getObjectVolatile(array, offset);

				if (obj == BORROWED) {
					if (unsafe().compareAndSwapObject(array, offset, obj, t)) {
						return;
					} else {
						break;
					}
				}
			}

			WAIT_STRATEGY.idle(i);
		}
	}

	@Override
	public void close() throws IOException {
		isClosing = true;

		for (int i = 0; i < 1000; i++) {
			int count = 0;
			for (int j = 0; j < size; j++) {
				if (unsafe().getObjectVolatile(array, offset(j)) != BORROWED) {
					count++;
				}
			}

			if (count == size) {
				break;
			}

			try {
				TimeUnit.MILLISECONDS.sleep(15);
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		}

		for (int i = 0; i < closeList.length; i++) {
			Object obj = closeList[i];

			if (obj instanceof AutoCloseable) {
				try {
					((AutoCloseable) obj).close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}

	private static final long offset(int key) {
		return ((long) key << ASHIFT) + ABASE;
	}

	static {
		try {
			ABASE = unsafe().arrayBaseOffset(Object[].class);

			int scale = unsafe().arrayIndexScale(Object[].class);
			if ((scale & (scale - 1)) != 0) {
				throw new Error("array index scale not a power of two");
			}

			ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		final AtomicInteger counter = new AtomicInteger();

		final ConcurrentObjectPool<Integer> pool = new ConcurrentObjectPool<>(4, () -> counter.getAndIncrement());

		for (int i = 0; i < 8; i++) {
			new Thread(() -> {
				for (int j = 0; j < Long.MAX_VALUE; j++) {
					Integer obj = pool.borrow();
					pool.release(obj);

					if (j % 1_000_000 == 0) {
						System.out.println(Thread.currentThread().getName() + ": " + j);
					}
				}
			}, "pooltest-" + i).start();
		}

		Thread.sleep(Long.MAX_VALUE);

		pool.close();

	}

}
