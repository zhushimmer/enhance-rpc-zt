package rpc.turbo.protocol;

/**
 * 压缩类型
 * 
 * @author zhushimmer
 *
 */
public interface CompressType {
	public static final byte NO = 0;
	public static final byte LZ4 = 1;
	public static final byte SNAPPY = 2;
	public static final byte GZIP = 3;
}
