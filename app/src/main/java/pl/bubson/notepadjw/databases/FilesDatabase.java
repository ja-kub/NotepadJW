package pl.bubson.notepadjw.databases;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Created by Kuba on 2017-07-18.
 * <p>
 * Used to access database which contains files contents, e.g. for searching them
 */

public class FilesDatabase {

    //The columns we'll include in the dictionary table
    public static final String COL_FILE_PATH = "FILE_PATH";
    public static final String COL_KEYWORDS = "KEYWORDS";
    private static final String TAG = "FilesDatabase";
    private static final String DATABASE_NAME = "FILES";
    private static final String FTS_VIRTUAL_TABLE = "FTS";
    private static final int DATABASE_VERSION = 1;

    private final DatabaseOpenHelper mDatabaseOpenHelper;

    public FilesDatabase(Context context, File mainDirectory) {
        mDatabaseOpenHelper = new DatabaseOpenHelper(context, mainDirectory);
    }

    public Cursor getWordMatches(String query, String[] columns) {
        String selection = COL_KEYWORDS + " MATCH ?";
        String[] selectionArgs = new String[]{query + "*"};

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
        Log.d(TAG, "refreshData: start");
        mDatabaseOpenHelper.refreshData();
        Log.d(TAG, "refreshData: end");
    }

    public void openDb() {
        mDatabaseOpenHelper.getReadableDatabase();
    }

    public void addFileOrDir(final File fileOrDir) {
        new Thread(new Runnable() {
            public void run() {
                mDatabaseOpenHelper.addFile(fileOrDir, fileOrDir.getName());
            }
        }).start();
    }

    public void updateFile(final File file) {
        new Thread(new Runnable() {
            public void run() {
                mDatabaseOpenHelper.updateKeywords(file);
            }
        }).start();
    }

    public void renameFileOrDir(final File oldFile, final File newFile) {
        new Thread(new Runnable() {
            public void run() {
                mDatabaseOpenHelper.updatePathAndKeywords(oldFile, newFile);
            }
        }).start();
    }

    private static class DatabaseOpenHelper extends SQLiteOpenHelper {

        private static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + FTS_VIRTUAL_TABLE +
                        " USING fts3 (" +
                        COL_FILE_PATH + ", " +
                        COL_KEYWORDS + ")";
        private final Context mHelperContext;
        private final File directoryToSearch;
        private SQLiteDatabase mDatabase;

        DatabaseOpenHelper(Context context, File directory) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mHelperContext = context;
            directoryToSearch = directory;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "onCreate FilesDatabase called");
            mDatabase = db;
            mDatabase.execSQL(FTS_TABLE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.v(TAG, "onUpgrade called");
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            onCreate(db);
        }

        private void refreshData() {
            new Thread(new Runnable() {
                public void run() {
                    mDatabase = getReadableDatabase();
                    mDatabase.execSQL("DELETE FROM " + FTS_VIRTUAL_TABLE);
                    loadFiles();
                }
            }).start();
        }

        private void loadFiles() {
            Log.d("loadFiles", "loadFiles started");
            loadFilesRecursive(directoryToSearch);
            Log.d("loadFiles", "loadFiles ended");
        }

        private void loadFilesRecursive(File fileOrDirectory) {
            if (fileOrDirectory.isDirectory()) {
                addFile(fileOrDirectory, fileOrDirectory.getName());
                for (File child : fileOrDirectory.listFiles()) loadFilesRecursive(child);
            } else {
                String keywords = getFileKeywords(fileOrDirectory);
                addFile(fileOrDirectory, keywords);
            }
        }

        private void addFile(File fileOrDirectory, String keywords) {
            String filePath = fileOrDirectory.getAbsolutePath();
            long id = addRecord(filePath, keywords);
            if (id < 0) {
                Log.e(TAG, "unable to add file: " + filePath);
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
                return Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT).toString();
            } else {
                try {
                    return Html.fromHtml(htmlContent).toString();
                } catch (Exception e) {
                    e.printStackTrace();
                    return "";
                }
            }
        }

        long addRecord(String filePath, String keywords) {
            ContentValues initialValues = new ContentValues();
            initialValues.put(COL_FILE_PATH, filePath);
            initialValues.put(COL_KEYWORDS, keywords);
            return mDatabase.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }

        void updateKeywords(File file) {
            String filePath = file.getAbsolutePath();
            String keywords = getFileKeywords(file);
            ContentValues cv = new ContentValues();
            cv.put(COL_KEYWORDS, keywords);
            long id = mDatabase.update(FTS_VIRTUAL_TABLE, cv, COL_FILE_PATH + "=?", new String[]{filePath});
            if (id < 0) {
                Log.e(TAG, "unable to update file: " + filePath);
            }
        }

        void updatePathAndKeywords(File oldFile, File newFile) {
            String oldFilePath = oldFile.getAbsolutePath();
            String newFilePath = newFile.getAbsolutePath();
            String keywords = newFile.isDirectory()? newFile.getName() : getFileKeywords(newFile);
            ContentValues cv = new ContentValues();
            cv.put(COL_FILE_PATH, newFilePath);
            cv.put(COL_KEYWORDS, keywords);
            long id = mDatabase.update(FTS_VIRTUAL_TABLE, cv, COL_FILE_PATH + "=?", new String[]{oldFilePath});
            if (id < 0) {
                Log.e(TAG, "unable to update row: " + oldFilePath);
            }
        }

        @NonNull
        private String getFileKeywords(File file) {
            return file.getName() + " " + openHtmlFile(file);
        }
    }
}