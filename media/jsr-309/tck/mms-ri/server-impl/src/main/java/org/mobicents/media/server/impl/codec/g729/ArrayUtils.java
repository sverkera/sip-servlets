package org.mobicents.media.server.impl.codec.g729;

public class ArrayUtils {

	public static float[] subArray(float[] array, int start) {
		int nl = array.length - start;
		float[] ret = new float[nl];
		for(int q=start; q<array.length; q++) {
			ret[q-start] = array[q];
		}
		return ret;
	}

	public static void replace(float[] array, int start, float[] tmp) {
		
		for(int q=start; q<array.length; q++) {
			array[q] = tmp[q-start];
		}
	}
}