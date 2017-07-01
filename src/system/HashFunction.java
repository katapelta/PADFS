package system;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.stream.LongStream;

import system.SystemEnvironment.Constants;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class HashFunction {
	private long a = 6511700658071011329L, // very pretty prime numbers
				 b = 8319481577260187649L,
				 n = 8817543667284705281L, // very hard to find prime number
				 m = Constants.numLabels;
		
	public Long evaluate(String x){
		LongBuffer longBuf = null;
		if( x != null){
						
			try {
				/* convert the String as a big endian byte buffer */
				ByteBuffer original = ByteBuffer.wrap(x.getBytes(Constants.charset));
				original.order(ByteOrder.BIG_ENDIAN);
				
				/* convert the byte buffer as a long buffer 
				 * appending 0 bytes, if necessary, to have a number of bytes multiple of the "long size"
				 */
				int longSize = Long.SIZE/Byte.SIZE;
				int newCapacity = original.capacity();
				int reminder = newCapacity%longSize;
				if(reminder > 0)
					newCapacity = newCapacity+(longSize-reminder);
				
				ByteBuffer dest = ByteBuffer.allocate(newCapacity);
				dest.mark();
				dest.put(original);
				dest.reset();
				longBuf = dest.asLongBuffer();
				
				/* convert to array of long */
				long b[] = new long[longBuf.capacity()];
				longBuf.get(b);
				
				/* sum up all long values letting overflow them */
				Long xLong = LongStream.of(b).sum();
				
				/* complete the hash function evaluation */
				long returnedVal = 	Long.remainderUnsigned(Long.remainderUnsigned((this.a * xLong) + this.b , this.n),this.m);
				return returnedVal;
				
			} catch (UnsupportedEncodingException e) {
				PadFsLogger.log(LogLevel.ERROR,"[HashFunction]" + e.getMessage());
			}
		
		}
		
		return null;
	}
}
