package com.ghouse.socialraven.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class GenericUtil {

	static Logger log = LoggerFactory.getLogger(GenericUtil.class);

	public static final String MM_DD_YYYY = "MM-dd-yyyy";
	public static final String DD_MMM_YYYY = "dd MMM yyyy";

	public static LocalDate getLocalDate(String date) {
		return getLocalDate(date, MM_DD_YYYY);
	}

	private static LocalDate getLocalDate(String date, String format) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
		try {
			LocalDate localDate = LocalDate.parse(date, formatter);
			return localDate;
		} catch (Exception exp) {
			log.error("failed to parse date ", exp);
			return null;
		}
	}

	public static String convertToString(LocalDate date) {
		return convertToString(date, MM_DD_YYYY);
	}

	private static String convertToString(LocalDate date, String format) {
		return date.format(DateTimeFormatter.ofPattern(format));
	}

	public static String convertToTitle(LocalDate date) {
		return convertToTitle(date, DD_MMM_YYYY);
	}

	public static String convertToTitle(LocalDate date, String format) {
		return date.format(DateTimeFormatter.ofPattern(DD_MMM_YYYY));
	}
	
	public static void wait(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException exp) {
		}
	}

}
