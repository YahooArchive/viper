package com.yahoo.viper.util;

public class Utils {
	private static long currentTime;

	public static long getActualTime() {
		currentTime = System.currentTimeMillis();
		return currentTime;
	}
}