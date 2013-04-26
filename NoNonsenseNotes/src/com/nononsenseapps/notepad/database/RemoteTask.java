package com.nononsenseapps.notepad.database;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

public class RemoteTask extends DAO {

	// SQL convention says Table name should be "singular"
	public static final String TABLE_NAME = "remotetask";

	public static final String CONTENT_TYPE = "vnd.android.cursor.item/vnd.nononsenseapps."
			+ TABLE_NAME;

	public static final Uri URI = Uri.withAppendedPath(
			Uri.parse(MyContentProvider.SCHEME + MyContentProvider.AUTHORITY),
			TABLE_NAME);

	public static final int BASEURICODE = 501;
	public static final int BASEITEMCODE = 502;

	public static void addMatcherUris(UriMatcher sURIMatcher) {
		sURIMatcher
				.addURI(MyContentProvider.AUTHORITY, TABLE_NAME, BASEURICODE);
		sURIMatcher.addURI(MyContentProvider.AUTHORITY, TABLE_NAME + "/#",
				BASEITEMCODE);
	}

	public static Uri getUri(final long id) {
		return Uri.withAppendedPath(URI, Long.toString(id));
	}

	public static class Columns implements BaseColumns {

		private Columns() {
		}

		public static final String ACCOUNT = "account";
		public static final String REMOTEID = "remoteid";
		public static final String UPDATED = "updated";
		public static final String DBID = "dbid";
		// Reserved columns, depending on what different services needs
		public static final String FIELD1 = "field1";
		public static final String FIELD2 = "field2";
		public static final String FIELD3 = "field3";
		public static final String FIELD4 = "field4";
		public static final String FIELD5 = "field5";

		public static final String[] FIELDS = { _ID, DBID, REMOTEID, UPDATED,
				ACCOUNT, FIELD1, FIELD2, FIELD3, FIELD4, FIELD5 };
	}

	/**
	 * Main table to store data
	 */
	public static final String CREATE_TABLE = new StringBuilder("CREATE TABLE ")
			.append(TABLE_NAME).append("(").append(Columns._ID)
			.append(" INTEGER PRIMARY KEY,").append(Columns.ACCOUNT)
			.append(" TEXT NOT NULL,").append(Columns.DBID)
			.append(" INTEGER NOT NULL,").append(Columns.UPDATED)
			.append(" INTEGER NOT NULL,").append(Columns.REMOTEID)
			.append(" TEXT NOT NULL,").append(Columns.FIELD1).append(" TEXT,")
			.append(Columns.FIELD2).append(" TEXT,").append(Columns.FIELD3)
			.append(" TEXT,").append(Columns.FIELD4).append(" TEXT,")
			.append(Columns.FIELD5).append(" TEXT,").append("FOREIGN KEY(")
			.append(Columns.DBID).append(") REFERENCES ")
			.append(Task.TABLE_NAME).append("(").append(Task.Columns._ID)
			.append(") ON DELETE CASCADE").append(")").toString();

	// milliseconds since 1970-01-01 UTC
	public Long updated = null;

	public Long dbid = null;
	public String account = null;
	public String remoteId = null;
	public String field1 = null;
	public String field2 = null;
	public String field3 = null;
	public String field4 = null;
	public String field5 = null;

	// Not stored as separate field
	public long listdbid = -1;

	public RemoteTask() {

	}

	/**
	 * None of the fields may be null!
	 * 
	 * @param dbid
	 * @param remoteId
	 * @param updated
	 * @param account
	 */
	public RemoteTask(final Long dbid, final String remoteId,
			final Long updated, final String account) {
		this.dbid = dbid;
		this.remoteId = remoteId;
		this.updated = updated;
		this.account = account;
	}

	public RemoteTask(final Cursor c) {
		_id = c.getLong(0);
		dbid = c.getLong(1);
		remoteId = c.getString(2);
		updated = c.getLong(3);
		account = c.getString(4);

		field1 = c.getString(5);
		field2 = c.getString(6);
		field3 = c.getString(7);
		field4 = c.getString(8);
		field5 = c.getString(9);
	}

	public RemoteTask(final Uri uri, final ContentValues values) {
		this(Long.parseLong(uri.getLastPathSegment()), values);
	}

	public RemoteTask(final long id, final ContentValues values) {
		this(values);
		_id = id;
	}

	public RemoteTask(final ContentValues values) {
		dbid = values.getAsLong(Columns.DBID);
		remoteId = values.getAsString(Columns.REMOTEID);
		updated = values.getAsLong(Columns.UPDATED);
		account = values.getAsString(Columns.ACCOUNT);

		field1 = values.getAsString(Columns.FIELD1);
		field2 = values.getAsString(Columns.FIELD2);
		field3 = values.getAsString(Columns.FIELD3);
		field4 = values.getAsString(Columns.FIELD4);
		field5 = values.getAsString(Columns.FIELD5);
	}

	@Override
	public ContentValues getContent() {
		final ContentValues values = new ContentValues();

		values.put(Columns.DBID, dbid);
		values.put(Columns.REMOTEID, remoteId);
		values.put(Columns.UPDATED, updated);
		values.put(Columns.ACCOUNT, account);
		values.put(Columns.FIELD1, field1);
		values.put(Columns.FIELD2, field2);
		values.put(Columns.FIELD3, field3);
		values.put(Columns.FIELD4, field4);
		values.put(Columns.FIELD5, field5);

		return values;

	}

	@Override
	protected String getTableName() {
		return TABLE_NAME;
	}

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@Override
	public int save(final Context context) {
		int result = 0;
		if (_id < 1) {
			final Uri uri = context.getContentResolver().insert(getBaseUri(),
					getContent());
			if (uri != null) {
				_id = Long.parseLong(uri.getLastPathSegment());
				result++;
			}
		}
		else {
			result += context.getContentResolver().update(getUri(),
					getContent(), null, null);
		}
		return result;
	}

	/**
	 * Returns a where clause that can be used to fetch the tasklist that is
	 * associated with this remote object. As argument, use remoteid, account
	 * 
	 * @return
	 */
	public String getTaskWithRemoteClause() {
		return new StringBuilder(Task.Columns.DBLIST + " IS ? AND ")
				.append(BaseColumns._ID).append(" IN (SELECT ")
				.append(Columns.DBID).append(" FROM ").append(TABLE_NAME)
				.append(" WHERE ").append(Columns.REMOTEID)
				.append(" IS ? AND ").append(Columns.ACCOUNT).append(" IS ?)")
				.toString();
	}

	public String[] getTaskWithRemoteArgs() {
		return new String[] { Long.toString(listdbid), remoteId, account };
	}

	/**
	 * Returns a where clause that limits the tasklists to those that do not
	 * have a remote version.
	 * 
	 * Combine with account
	 */
	public String getTaskWithoutRemoteClause() {
		return new StringBuilder(Task.Columns.DBLIST + " IS ? AND ")
				.append(BaseColumns._ID).append(" NOT IN (SELECT ")
				.append(Columns.DBID).append(" FROM ").append(TABLE_NAME)
				.append(" WHERE ").append(Columns.ACCOUNT).append(" IS ?)")
				.toString();
	}

	public String[] getTaskWithoutRemoteArgs() {
		return new String[] { Long.toString(listdbid), account };
	}
}