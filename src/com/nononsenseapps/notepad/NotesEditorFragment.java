package com.nononsenseapps.notepad;

import java.util.Calendar;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.nononsenseapps.notepad.interfaces.DeleteActionListener;
import com.nononsenseapps.notepad.interfaces.onNewNoteCreatedListener;
import com.nononsenseapps.ui.TextPreviewPreference;

public class NotesEditorFragment extends Fragment implements TextWatcher,
		OnDateSetListener, OnClickListener {

	// Two ways of expressing: "Mon, 16 Jan"
	public final static String ANDROIDTIME_FORMAT = "%a, %e %b";
	public final static String DATEFORMAT_FORMAT = "E, d MMM";
	/*
	 * Creates a projection that returns the note ID and the note contents.
	 */
	public static final String[] PROJECTION = new String[] { NotePad.Notes._ID,
			NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE,
			NotePad.Notes.COLUMN_NAME_DUE_DATE,
			NotePad.Notes.COLUMN_NAME_GTASKS_ID };

	// A label for the saved state of the activity
	public static final String ORIGINAL_NOTE = "origContent";
	public static final String ORIGINAL_TITLE = "origTitle";
	public static final String ORIGINAL_DUE = "origDue";
	public static final String ORIGINAL_DUE_STATE = "origDueState";

	// Argument keys
	public static final String KEYID = "noteid";

	// This Activity can be started by more than one action. Each action is
	// represented
	// as a "state" constant
	public static final int STATE_EDIT = 0;
	public static final int STATE_INSERT = 1;

	protected static final int DATE_DIALOG_ID = 999;

	// These fields are strictly used for the date picker dialog. They should
	// not be used to get the notes due date!
	public int year;
	public int month;
	public int day;

	// This object is used to save the correct date in the database.
	private Time noteDueDate;
	private boolean dueDateSet = false;

	// gTask ID
	private String gTaskID = null;

	// Global mutable variables
	private int mState;
	private Uri mUri;
	private Cursor mCursor;
	private EditText mText;
	private String mOriginalNote;
	private String mOriginalTitle;
	private String mOriginalDueDate;

	private boolean doSave = true;

	private long id;

	private boolean timeToDie;

	private Object shareActionProvider = null; // Must be object otherwise HC
												// will crash

	private Activity activity;

	private onNewNoteCreatedListener onNewNoteListener = null;

	private EditText mTitle;

	private Button mDueDate;
	private boolean mOriginalDueState;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
	}

	public void setOnNewNoteCreatedListener(onNewNoteCreatedListener listener) {
		this.onNewNoteListener = listener;
	}

	/**
	 * Create a new instance of DetailsFragment, initialized to show the text at
	 * 'index'.
	 */
	public static NotesEditorFragment newInstance(long id) {
		NotesEditorFragment f = new NotesEditorFragment();

		// Supply index input as an argument.
		Log.d("NotesEditorFragment", "Creating Fragment, args: " + id);
		Bundle args = new Bundle();
		args.putLong(KEYID, id);
		f.setArguments(args);

		return f;
	}

	public static Uri getUriFrom(long id) {
		return Uri.withAppendedPath(NotePad.Notes.CONTENT_URI,
				String.valueOf(id));
	}

	/**
	 * If we are supposed to open a new note, the arguments will contain
	 * "content".
	 * 
	 * @param savedInstanceState
	 */
	private void openNote(Bundle savedInstanceState) {
		Log.d("NotesEditorFragment", "Does argument have id? "
				+ getArguments().containsKey(KEYID));
		Log.d("NotesEditorFragment", "OpenNOTe: Id is " + id);
		if (id != -1) {
			// Existing note
			mState = STATE_EDIT;
			mUri = getUriFrom(id);
			Log.d("NotesEditorFragment",
					"Editing existing note, uri = " + mUri.toString());
		} else {
			// New note
			mState = STATE_INSERT;
			mUri = activity.getContentResolver().insert(
					NotePad.Notes.CONTENT_URI, null);
			Log.d("NotesEditorFragment",
					"Inserting new note, uri = " + mUri.toString());
			/*
			 * If the attempt to insert the new note fails, shuts down this
			 * Activity. The originating Activity receives back RESULT_CANCELED
			 * if it requested a result. Logs that the insert failed.
			 */
			if (mUri == null) {
				// Closes the activity.
				activity.finish();
				return;
			}

			// Notify list of the new note
			if (onNewNoteListener != null)
				onNewNoteListener.onNewNoteCreated(getIdFromUri(mUri));
		}
		/*
		 * Using the URI passed in with the triggering Intent, gets the note or
		 * notes in the provider. Note: This is being done on the UI thread. It
		 * will block the thread until the query completes. In a sample app,
		 * going against a simple provider based on a local database, the block
		 * will be momentary, but in a real app you should use
		 * android.content.AsyncQueryHandler or android.os.AsyncTask.
		 */
		mCursor = activity.managedQuery(mUri, // The URI that gets multiple
												// notes from
				// the provider.
				PROJECTION, // A projection that returns the note ID and note
							// content for each note.
				null, // No "where" clause selection criteria.
				null, // No "where" clause selection values.
				null // Use the default sort order (modification date,
						// descending)
				);
		// Or Honeycomb will crash
		activity.stopManagingCursor(mCursor);

		// Switches the state to EDIT so the title can be modified.
		mState = STATE_EDIT;

		if (savedInstanceState != null) {
			mOriginalNote = savedInstanceState.getString(ORIGINAL_NOTE);
			mOriginalDueDate = savedInstanceState.getString(ORIGINAL_DUE);
			mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
			mOriginalDueState = savedInstanceState
					.getBoolean(ORIGINAL_DUE_STATE);
		}
	}

	private static long getIdFromUri(Uri uri) {
		String newId = uri.getPathSegments().get(
				NotePad.Notes.NOTE_ID_PATH_POSITION);
		return Long.parseLong(newId);
	}
	
	private boolean hasNoteChanged() {
		return !(mText.getText().toString().equals(mOriginalNote)
				&& mTitle.getText().toString().equals(mOriginalTitle)
				&& dueDateSet == mOriginalDueState
				&& (!dueDateSet || (dueDateSet && noteDueDate.format3339(false).equals(mOriginalDueDate))));
	}

	/**
	 * Replaces the current note contents with the text and title provided as
	 * arguments.
	 * 
	 * @param text
	 *            The new note contents to use.
	 */
	private final void updateNote(String title, String text, String due) {

		// Only updates if the text is different from original content
		// Only compare dates if there actually is a previous date to compare
		if (!hasNoteChanged()) {
			Log.d("NotesEditorFragment", "Updating (not) note");
			// Do Nothing in this case.
		} else {
			Log.d("NotesEditorFragment", "Updating note");
			Log.d("NotesEditorFragment", "" + text.equals(mOriginalNote));
			Log.d("NotesEditorFragment", "" + title.equals(mOriginalTitle));
			Log.d("NotesEditorFragment", "" + due + " " + mOriginalDueDate);
			Log.d("NotesEditorFragment", "" + (dueDateSet == mOriginalDueState));

			// Sets up a map to contain values to be updated in the provider.
			ContentValues values = new ContentValues();

			// Add new modification time
			values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
					System.currentTimeMillis());

			// If no title was provided as an argument, create one from the note
			// text.
			if (title.isEmpty()) {
				title = makeTitle(text);
			}

			// If the action is to insert a new note, this creates an initial
			// title
			// for it.
			// if (mState == STATE_INSERT) {
			// In the values map, sets the value of the title
			// values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
			// } else if (title != null) {
			// In the values map, sets the value of the title
			values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
			// }

			// This puts the desired notes text into the map.
			values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

			// Put the due-date in
			if (dueDateSet) {
				values.put(NotePad.Notes.COLUMN_NAME_DUE_DATE, due);
			} else {
				values.put(NotePad.Notes.COLUMN_NAME_DUE_DATE, "");
			}

			// Put gTasks id in
			if (gTaskID != null && !gTaskID.isEmpty()) {
				values.put(NotePad.Notes.COLUMN_NAME_GTASKS_ID, gTaskID);
			}

			/*
			 * Updates the provider with the new values in the map. The ListView
			 * is updated automatically. The provider sets this up by setting
			 * the notification URI for query Cursor objects to the incoming
			 * URI. The content resolver is thus automatically notified when the
			 * Cursor for the URI changes, and the UI is updated. Note: This is
			 * being done on the UI thread. It will block the thread until the
			 * update completes. In a sample app, going against a simple
			 * provider based on a local database, the block will be momentary,
			 * but in a real app you should use
			 * android.content.AsyncQueryHandler or android.os.AsyncTask.
			 */
			Log.d("NotesEditorFragment", "URI: " + mUri);
			Log.d("NotesEditorFragment", "values: " + values.toString());
			activity.getContentResolver().update(mUri, // The URI for the
														// record to
					// update.
					values, // The map of column names and new values to apply
							// to
							// them.
					null, // No selection criteria are used, so no where columns
							// are
							// necessary.
					null // No where columns are used, so no where arguments are
							// necessary.
					);
		}
	}

	private String makeTitle(String text) {
		String title = null;
		// Get the note's length
		int length = text.length();

		// Sets the title by getting a substring of the text that is 31
		// characters long
		// or the number of characters in the note plus one, whichever is
		// smaller.
		title = text.substring(0, Math.min(30, length));
		int firstNewLine = title.indexOf("\n");

		// Only use the first line of text as title
		if (firstNewLine > 0) {
			title = title.substring(0, firstNewLine);
		} else if (firstNewLine == 0) {
			title = "First line empty...";
		}

		// If the resulting length is more than 30 characters, chops off any
		// trailing spaces
		if (title.length() > 30) {
			int lastSpace = title.lastIndexOf(' ');
			if (lastSpace > 0) {
				title = title.substring(0, lastSpace);
			}
		}
		return title;
	}

	/**
	 * This helper method cancels the work done on a note. It deletes the note
	 * if it was newly created, or reverts to the original text of the note i
	 */
	private final void cancelNote() {
		if (mCursor != null) {
			if (mState == STATE_EDIT) {
				// Put the original note text back into the database
				mCursor.close();
				mCursor = null;
				// ContentValues values = new ContentValues();
				// values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
				// getActivity().getContentResolver().update(mUri, values, null,
				// null);
				openNote(null);
				showTheNote();
			} else if (mState == STATE_INSERT) {
				// We inserted an empty note, make sure to delete it
				deleteNote();
			}
		}
	}

	/**
	 * Take care of deleting a note. Simply deletes the entry.
	 */
	private final void deleteNote() {
		Log.d("NotesEditorFragment", "Deleting note");
		if (mCursor != null) {
			this.id = -1;
			mCursor.close();
			mCursor = null;
			getActivity().getContentResolver().delete(mUri, null, null);
			mText.setText("");
		}
	}

	private void copyText(String text) {
		ClipboardManager clipboard = (ClipboardManager) activity
				.getSystemService(Context.CLIPBOARD_SERVICE);
		// ICS style
		clipboard.setPrimaryClip(ClipData.newPlainText("Note", text));
		// Gingerbread style.
		// clipboard.setText(text);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d("NotesEditorFragment", "onCreate");
		super.onCreate(savedInstanceState);
		// To get the call back to add items to the menu
		setHasOptionsMenu(true);

		id = getArguments().getLong(KEYID);

		noteDueDate = new Time(Time.getCurrentTimezone());

		Calendar c = Calendar.getInstance();
		year = c.get(Calendar.YEAR);
		month = c.get(Calendar.MONTH);
		day = c.get(Calendar.DAY_OF_MONTH);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null) {
			// We have different layouts, and in one of them this
			// fragment's containing frame doesn't exist. The fragment
			// may still be created from its saved state, but there is
			// no reason to try to create its view hierarchy because it
			// won't be displayed. Note this is not needed -- we could
			// just run the code below, where we would create and return
			// the view hierarchy; it would just never be used.
			Log.d("NotesEditorFragment", "Should return null");
			timeToDie = true;
			return null;
		}
		Log.d("NotesEditorFragment", "onCreateView");

		int layout = R.layout.editor_layout;
		// if (FragmentLayout.lightTheme) {
		// layout = R.layout.note_editor_light;
		// }

		// Gets a handle to the EditText in the the layout.
		ScrollView theView = (ScrollView) inflater.inflate(layout, container, false);
		// This is to prevent the view from setting focus (and bringing up the keyboard)
		theView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
		theView.setFocusable(true);
		theView.setFocusableInTouchMode(true);
		theView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				v.requestFocusFromTouch();
				return false;
			}
	    });
		
		// Main note edit text
		mText = (EditText) theView.findViewById(R.id.noteBox);
		mText.addTextChangedListener(this);
		// Title edit text
		mTitle = (EditText) theView.findViewById(R.id.titleBox);
		mTitle.addTextChangedListener(this);
		// dueDate button
		mDueDate = (Button) theView.findViewById(R.id.dueDateBox);
		mDueDate.setOnClickListener(this);

		ImageButton cancelButton = (ImageButton) theView
				.findViewById(R.id.dueCancelButton);
		cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				// TODO
				// Run on UI thread?
				clearDueDate();

			}

		});

		return theView;
	}

	private void clearDueDate() {
		if (mDueDate != null) {
			mDueDate.setText(getText(R.string.editor_due_date_hint));
			// set year, month, day variables to today
			Calendar c = Calendar.getInstance();
			year = c.get(Calendar.YEAR);
			month = c.get(Calendar.MONTH);
			day = c.get(Calendar.DAY_OF_MONTH);
		}
		dueDateSet = false;
		
		// Remember to update share intent
		setActionShareIntent();
	}

	private void setFontSettings() {
		if (mText != null) {
			// set characteristics from settings
			float size = PreferenceManager.getDefaultSharedPreferences(
					getActivity()).getInt(
					NotesPreferenceFragment.KEY_FONT_SIZE_EDITOR,
					R.integer.default_editor_font_size);
			Typeface tf = TextPreviewPreference.getTypeface(PreferenceManager
					.getDefaultSharedPreferences(getActivity()).getString(
							NotesPreferenceFragment.KEY_FONT_TYPE_EDITOR,
							NotesPreferenceFragment.SANS));

			mText.setTextSize(size);
			mText.setTypeface(tf);
			mTitle.setTextSize(size);
			mTitle.setTypeface(tf);
		}
	}

	@Override
	public void onActivityCreated(Bundle saves) {
		super.onActivityCreated(saves);
		// if Time to Die, do absolutely nothing since this fragment will go bye
		// bye

		if (timeToDie) {
			Log.d("NotesEditorFragment",
					"onActivityCreated, but it is time to die so doing nothing...");
		} else {
			openNote(saves);
			showTheNote();
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		Log.d("NotesEditorFragment", "onCreateOptions");
		if (timeToDie) {
			Log.d("NotesEditorFragment",
					"onCreateOptions, but it is time to die so doing nothing...");
		} else {
			// Inflate menu from XML resource
			// if (FragmentLayout.lightTheme)
			// inflater.inflate(R.menu.editor_options_menu_light, menu);
			// else
			inflater.inflate(R.menu.editor_options_menu, menu);

			if (FragmentLayout.AT_LEAST_ICS) {
				// Set delete listener to this
				MenuItem actionItem = menu.findItem(R.id.action_delete);

				DeleteActionProvider actionProvider = (DeleteActionProvider) actionItem
						.getActionProvider();

				// Make sure containing activity implements listner interface
				actionProvider
						.setDeleteActionListener((DeleteActionListener) activity);

				// Set default intent on ShareProvider and set shareListener to
				// this so
				// we can update with current note
				// Set file with share history to the provider and set the share
				// intent.
				MenuItem shareItem = menu
						.findItem(R.id.editor_share_action_provider_action_bar);

				ShareActionProvider shareProvider = (ShareActionProvider) shareItem
						.getActionProvider();
				shareProvider
						.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);

				this.shareActionProvider = shareProvider;
				
				// Note that you can set/change the intent any time,
				// say when the user has selected an image.
				setActionShareIntent();
			}

			// Only add extra menu items for a saved note
			if (mState == STATE_EDIT) {
				// Append to the
				// menu items for any other activities that can do stuff with it
				// as well. This does a query on the system for any activities
				// that
				// implement the ALTERNATIVE_ACTION for our data, adding a menu
				// item
				// for each one that is found.
				Intent intent = new Intent(null, mUri);
				intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
				menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
						new ComponentName(activity, NotesEditorFragment.class),
						null, intent, 0, null);
			}
		}
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		if (timeToDie) {
			Log.d("NotesEditorFragment",
					"onPrepareOptionsMenu, but it is time to die so doing nothing...");
		} else {
			// Check if note has changed and enable/disable the revert option
			if (mCursor != null) {
				int colNoteIndex = mCursor
						.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
				// if (colNoteIndex != -1)
				String savedNote = mCursor.getString(colNoteIndex);
				String currentNote = mText.getText().toString();
				if (!hasNoteChanged()) {
					menu.findItem(R.id.menu_revert).setVisible(false);
				} else {
					menu.findItem(R.id.menu_revert).setVisible(true);
				}
			} else {
				menu.findItem(R.id.menu_revert).setVisible(false);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle all of the possible menu actions.
		switch (item.getItemId()) {
		case R.id.menu_revert:
			cancelNote();
			Toast.makeText(activity, "Reverted changes", Toast.LENGTH_SHORT)
					.show();
			break;
		case R.id.menu_copy:
			copyText(makeShareText());
			Toast.makeText(activity, "Note placed in clipboard",
					Toast.LENGTH_SHORT).show();
			break;
		case R.id.menu_share:
			shareNote();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private String makeShareText() {
		String note = "";

		if (mTitle != null)
			note = mTitle.getText().toString() + "\n";

		if (dueDateSet && noteDueDate != null) {
			note = note + "due date: "
					+ noteDueDate.format(NotesEditorFragment.ANDROIDTIME_FORMAT)
					+ "\n";
		}

		if (mText != null)
			note = note + "\n" + mText.getText().toString();

		return note;
	}

	private void shareNote() {
		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("text/plain");
		share.putExtra(Intent.EXTRA_TEXT, makeShareText());
		startActivity(Intent.createChooser(share, "Share note"));
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!timeToDie) {
			Log.d("NotesEditorFragment", "onResume");

			// Settings might have been changed
			setFontSettings();
			// Closed with new note being only note. That was deleted. Requery
			// and
			// start new note.
			if (mCursor == null || mCursor.isClosed()) {
				openNote(null);
			}
			showTheNote();
		}
	}

	private void showTheNote() {
		if (mCursor != null && !mCursor.isClosed()) {
			// Requery in case something changed while paused (such as the
			// title)
			mCursor.requery();

			/*
			 * Moves to the first record. Always call moveToFirst() before
			 * accessing data in a Cursor for the first time. The semantics of
			 * using a Cursor are that when it is created, its internal index is
			 * pointing to a "place" immediately before the first record.
			 */
			mCursor.moveToFirst();

			// Modifies the window title for the Activity according to the
			// current Activity state.
			// if (mState == STATE_EDIT) {
			// // Set the title of the Activity to include the note title
			// int colTitleIndex = mCursor
			// .getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
			// String title = mCursor.getString(colTitleIndex);
			// Resources res = getResources();
			// String text = String.format(res.getString(R.string.title_edit),
			// title);
			// activity.setTitle(text);
			// // Sets the title to "create" for inserts
			// } else if (mState == STATE_INSERT) {
			// activity.setTitle(getText(R.string.title_create));
			// }

			/*
			 * onResume() may have been called after the Activity lost focus
			 * (was paused). The user was either editing or creating a note when
			 * the Activity paused. The Activity should re-display the text that
			 * had been retrieved previously, but it should not move the cursor.
			 * This helps the user to continue editing or entering.
			 */

			int colTitleIndex = mCursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
			String title = mCursor.getString(colTitleIndex);
			if (!title.equals(getString(android.R.string.untitled))) {
				mTitle.setText(title);
			}
			// Gets the note text from the Cursor and puts it in the TextView,
			// but doesn't change
			// the text cursor's position. setTextKeepState
			int colNoteIndex = mCursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
			String note = mCursor.getString(colNoteIndex);
			mText.setText(note);
			// Sets cursor at the end
			//mText.setSelection(note.length());

			int colDueIndex = mCursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_DUE_DATE);
			String due = mCursor.getString(colDueIndex);

			// update year, month, day here from database instead if they exist
			if (due == null || due.isEmpty()) {
				clearDueDate();
			} else {
				try {
					noteDueDate.parse3339(due);
					dueDateSet = true;
					mDueDate.setText(noteDueDate.format(ANDROIDTIME_FORMAT));
				} catch (TimeFormatException e) {
					noteDueDate.setToNow();
					dueDateSet = false;
				}
			}

			// Stores the original note text, to allow the user to revert
			// changes.
			if (mOriginalNote == null) {
				mOriginalNote = note;
			}
			if (mOriginalDueDate == null) {
				mOriginalDueDate = due;
				mOriginalDueState = dueDateSet;
			}
			if (mOriginalTitle == null) {
				mOriginalTitle = title;
			}
			
			// TODO
			// Set share intent on action provider in ICS
			//setActionShareIntent();

			// Request focus, will not open keyboard
			if (FragmentLayout.LANDSCAPE_MODE) {
				activity.findViewById(R.id.noteBox).requestFocus();
			}

			/*
			 * Something is wrong. The Cursor should always contain data. Report
			 * an error in the note.
			 */
		} else {
			activity.setTitle(getText(R.string.error_title));
			if (mText != null)
				mText.setText(getText(R.string.error_message));
		}
	}

	/**
	 * This method is called when an Activity loses focus during its normal
	 * operation, and is then later on killed. The Activity has a chance to save
	 * its state so that the system can restore it.
	 * 
	 * Notice that this method isn't a normal part of the Activity lifecycle. It
	 * won't be called if the user simply navigates away from the Activity.
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		// Save away the original text, so we still have it if the activity
		// needs to be killed while paused.
		outState.putString(ORIGINAL_NOTE, mOriginalNote);
		outState.putString(ORIGINAL_DUE, noteDueDate.format3339(false));
		outState.putBoolean(ORIGINAL_DUE_STATE, mOriginalDueState);
		outState.putString(ORIGINAL_TITLE, mOriginalTitle);
	}

	/**
	 * This method is called when the Activity loses focus.
	 * 
	 * For Activity objects that edit information, onPause() may be the one
	 * place where changes are saved. The Android application model is
	 * predicated on the idea that "save" and "exit" aren't required actions.
	 * When users navigate away from an Activity, they shouldn't have to go back
	 * to it to complete their work. The act of going away should save
	 * everything and leave the Activity in a state where Android can destroy it
	 * if necessary.
	 * 
	 * If the user hasn't done anything, then this deletes or clears out the
	 * note, otherwise it writes the user's work to the provider.
	 */
	@Override
	public void onPause() {
		super.onPause();
		Log.d("NotesEditorFragment", "onPause");

		/*
		 * Tests to see that the query operation didn't fail (see onCreate()).
		 * The Cursor object will exist, even if no records were returned,
		 * unless the query failed because of some exception or error.
		 */
		if (doSave && mCursor != null && mText != null && mTitle != null) {
			Log.d("NotesEditorFragment", "onPause Saving/Deleting Note");

			// Get the current note text.
			String text = mText.getText().toString();

			// Get title text
			String title = mTitle.getText().toString();

			/*
			 * If the Activity is in the midst of finishing and there is no text
			 * in the current note, returns a result of CANCELED to the caller,
			 * and deletes the note. This is done even if the note was being
			 * edited, the assumption being that the user wanted to "clear out"
			 * (delete) the note.
			 */
			// if (isFinishing() && (length == 0)) {
			if (text.isEmpty() && title.isEmpty()) {
				activity.setResult(Activity.RESULT_CANCELED);
				deleteNote();
				// Tell list to reselect after deletion
				this.onNewNoteListener.onNewNoteDeleted(getIdFromUri(mUri));

				/*
				 * Writes the edits to the provider. The note has been edited if
				 * an existing note was retrieved into the editor *or* if a new
				 * note was inserted. In the latter case, onCreate() inserted a
				 * new empty note into the provider, and it is this new note
				 * that is being edited.
				 */
			} else if (mState == STATE_EDIT) {
				// Creates a map to contain the new values for the columns
				updateNote(title, text, noteDueDate.format3339(false));
			} else if (mState == STATE_INSERT) {
				updateNote(title, text, noteDueDate.format3339(false));
				mState = STATE_EDIT;
			}
		}
	}

	/**
	 * Prevents this Fragment from saving the note on exit
	 */
	public void setNoSave() {
		doSave = false;
		if (mCursor != null) {
			mCursor.close();
			mCursor = null;
		}
	}
	
	private void setActionShareIntent() {
		if (FragmentLayout.AT_LEAST_ICS && shareActionProvider != null) {
			Intent share = new Intent(Intent.ACTION_SEND);
			share.setType("text/plain");
			share.putExtra(Intent.EXTRA_TEXT, makeShareText());

			((ShareActionProvider) shareActionProvider).setShareIntent(share);
		}
	}

	@Override
	public void afterTextChanged(Editable s) {
		setActionShareIntent();
	}
	
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// Don't care
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// Don't care
	}

	/**
	 * Once the user picks a date from the dialog, the date is received here and
	 * fields are updated, button text changed.
	 */
	@Override
	public void onDateSet(DatePicker view, int year, int monthOfYear,
			int dayOfMonth) {
		// Update variables and set the button text
		this.year = year;
		this.month = monthOfYear;
		this.day = dayOfMonth;

		Calendar c = Calendar.getInstance();

		c.set(year, monthOfYear, dayOfMonth);

		noteDueDate.set(dayOfMonth, monthOfYear, year);
		dueDateSet = true;
		
		// Remember to update share intent
		setActionShareIntent();

		final CharSequence timeToShow = DateFormat.format(DATEFORMAT_FORMAT, c);

		activity.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (mDueDate != null) {
					mDueDate.setText(timeToShow);
				}
			}

		});
	}

	@Override
	public void onClick(View v) {

		// activity.showDialog(DATE_DIALOG_ID);
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		Fragment prev = getFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		// Create and show the dialog.
		DatePickerDialogFragment newFragment = new DatePickerDialogFragment(
				this);
		newFragment.show(ft, "dialog");
	}

	public class DatePickerDialogFragment extends DialogFragment {
		private NotesEditorFragment mFragment;

		public DatePickerDialogFragment(NotesEditorFragment callback) {
			mFragment = callback;
		}

		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return new DatePickerDialog(getActivity(), mFragment,
					mFragment.year, mFragment.month, mFragment.day);
		}
	}
}