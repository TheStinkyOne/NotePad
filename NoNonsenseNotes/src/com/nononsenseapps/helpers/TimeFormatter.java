package com.nononsenseapps.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.nononsenseapps.notepad.R;

import android.content.Context;
import android.preference.PreferenceManager;

/**
 * A class that helps with displaying locale and preference specific dates
 * 
 */
public class TimeFormatter {

	public static Locale getLocale(final String lang) {
		final Locale locale;
		if (lang == null || lang.isEmpty()) {
			locale = Locale.getDefault();
		}
		else if (lang.length() == 5) {
			locale = new Locale(lang.substring(0, 2), lang.substring(3, 5));
		}
		else {
			locale = new Locale(lang.substring(0, 2));
		}
		return locale;
	}

	/**
	 * Formats date according to the designated locale
	 */
	public static String getLocalDateString(final Context context,
			final String lang, final String format, final long timeInMillis) {
		return getLocalFormatter(context, lang, format).format(
				new Date(timeInMillis));
	}

	/**
	 * Formats the date according to the locale the user has defined in settings
	 */
	public static String getLocalDateString(final Context context,
			final String format, final long timeInMillis) {
		return getLocalDateString(
				context,
				PreferenceManager.getDefaultSharedPreferences(context)
						.getString(context.getString(R.string.pref_locale), ""),
				format, timeInMillis);
	}

	/**
	 * Dont use for performance critical settings
	 */
	public static String getLocalDateStringLong(final Context context,
			final long time) {
		return getLocalFormatterLong(context).format(new Date(time));
	}

	/**
	 * Dont use for performance critical settings
	 */
	public static String getLocalDateStringShort(final Context context,
			final long time) {
		return getLocalFormatterShort(context).format(new Date(time));
	}

	private static SimpleDateFormat getLocalFormatter(final Context context,
			final String lang, final String format) {
		final Locale locale = getLocale(lang);
		return new SimpleDateFormat(format, locale);
	}

	/**
	 * Good for performance critical situations, like lists
	 */
	public static SimpleDateFormat getLocalFormatterLong(final Context context) {
		return getLocalFormatter(
				context,
				PreferenceManager.getDefaultSharedPreferences(context)
						.getString(context.getString(R.string.pref_locale), ""),
				PreferenceManager
						.getDefaultSharedPreferences(context)
						.getString(
								context.getString(R.string.key_pref_dateformat_long),
								context.getString(R.string.dateformat_long_1)));
	}

	/**
	 * Good for performance critical situations, like lists
	 */
	public static SimpleDateFormat getLocalFormatterShort(final Context context) {
		return getLocalFormatter(
				context,
				PreferenceManager.getDefaultSharedPreferences(context)
						.getString(context.getString(R.string.pref_locale), ""),
				PreferenceManager
						.getDefaultSharedPreferences(context)
						.getString(
								context.getString(R.string.key_pref_dateformat_short),
								context.getString(R.string.dateformat_short_1)));
	}

	/**
	 * Good for performance critical situations, like lists
	 */
	public static SimpleDateFormat getLocalFormatterWeekday(
			final Context context) {
		return getLocalFormatter(
				context,
				PreferenceManager.getDefaultSharedPreferences(context)
						.getString(context.getString(R.string.pref_locale), ""),
				context.getString(R.string.dateformat_weekday));
	}
}