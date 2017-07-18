package pl.bubson.notepadjw.databases;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Build;
import android.text.Html;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Kuba on 2017-07-18.
 *
 * Used to access database which contains files contents, e.g. for searching them
 */

public class FilesDatabase {

    private static final String TAG = "FilesDatabase";

    //The columns we'll include in the dictionary table
    public static final String COL_FILE = "FILE";
    public static final String COL_CONTENT = "CONTENT";

    private static final String DATABASE_NAME = "FILES";
    private static final String FTS_VIRTUAL_TABLE = "FTS";
    private static final int DATABASE_VERSION = 1;

    private final DatabaseOpenHelper mDatabaseOpenHelper;

    public FilesDatabase(Context context, File mainDirectory) {
        mDatabaseOpenHelper = new DatabaseOpenHelper(context, mainDirectory);
    }

    private static class DatabaseOpenHelper extends SQLiteOpenHelper {

        private final Context mHelperContext;
        private final File directoryToSearch;
        private SQLiteDatabase mDatabase;

        private static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + FTS_VIRTUAL_TABLE +
                        " USING fts3 (" +
                        COL_FILE + ", " +
                        COL_CONTENT + ")";

        DatabaseOpenHelper(Context context, File directory) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mHelperContext = context;
            directoryToSearch = directory;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.v(TAG, "onCreate called");
            mDatabase = db;
            mDatabase.execSQL(FTS_TABLE_CREATE);
            loadData();
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.v(TAG, "onUpgrade called");
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            onCreate(db);
        }

        void refreshData() {
            mDatabase = getReadableDatabase();
            mDatabase.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            mDatabase.execSQL(FTS_TABLE_CREATE);
            loadData();
        }

        private void loadData() {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        loadFiles();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        private void loadFiles() throws IOException {
            Log.v("loadFiles", "loadFiles started");
            loadFilesRecursive(directoryToSearch);
            Log.v("loadFiles", "loadFiles ended");
        }

        private void loadFilesRecursive(File fileOrDirectory) {
            if (fileOrDirectory.isDirectory()) {
                for (File child : fileOrDirectory.listFiles()) loadFilesRecursive(child);
            } else {
                String filePath = fileOrDirectory.getAbsolutePath();
                String content = fileOrDirectory.getName() + " " + openHtmlFile(fileOrDirectory); // include file name within search
                long id = addFile(filePath, content);
                if (id < 0) {
                    Log.e(TAG, "unable to add file: " + filePath);
                }
            }
        }

        private String openHtmlFile(File file) {
            String htmlContent = "";
            try {
                InputStream is = new FileInputStream(file);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                htmlContent = new String(buffer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return Html.fromHtml(htmlContent,Html.FROM_HTML_MODE_COMPACT).toString();
            } else {
                return Html.fromHtml(htmlContent).toString();
            }
        }

        public long addFile(String file, String content) {
            ContentValues initialValues = new ContentValues();
            initialValues.put(COL_FILE, file);
            initialValues.put(COL_CONTENT, content);

            return mDatabase.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }
    }

    public Cursor getWordMatches(String query, String[] columns) {
        String selection = COL_CONTENT + " MATCH ?";
        String[] selectionArgs = new String[] {query+"*"};

        return query(selection, selectionArgs, columns);
    }

    private Cursor query(String selection, String[] selectionArgs, String[] columns) {
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(FTS_VIRTUAL_TABLE);

        Cursor cursor = builder.query(mDatabaseOpenHelper.getReadableDatabase(),
                columns, selection, selectionArgs, null, null, null);

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    public void refreshData() {
        mDatabaseOpenHelper.refreshData();
    }
}