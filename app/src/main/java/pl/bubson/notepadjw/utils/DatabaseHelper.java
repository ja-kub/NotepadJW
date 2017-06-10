package pl.bubson.notepadjw.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import static pl.bubson.notepadjw.core.BookNamesMapping.filePrefixMap;
import static pl.bubson.notepadjw.core.BookNamesMapping.fileRegexMap;

/**
 * Created by Kuba on 2016-12-11.
 */
public class DatabaseHelper extends SQLiteAssetHelper {

    private static final String TAG = "DatabaseHelper";
    private static final int EXPECTED_CHAPTERS_IN_BIBLE = 1189;
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Bibles.db";
    private static final String TABLE_NAME = "Verses";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_CONTENTS = "contents";
    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    KEY_LANGUAGE + " TEXT, " +
                    KEY_FILE_NAME + " TEXT, " +
                    KEY_CONTENTS + " TEXT);";
    private Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void installDbFromAssetsIfNeeded() {
        Log.v(TAG, "installDbFromAssetsIfNeeded() executed");
//        this.getReadableDatabase().close(); // this would copy db from assets if it's not in place
        // experiment - will below line improve performance? This would also copy db from assets if it's not in place (as above line).
        this.getFile(Language.en, "1001060420-split133.xhtml"); // example chapter, Psalm 133:1
        Log.v(TAG, "installDbFromAssetsIfNeeded() finished");
    }

    public String getFile(Language language, String fileName) {
        String result = null;
        String select = "SELECT " + KEY_CONTENTS
                + " FROM " + TABLE_NAME
                + " WHERE " + KEY_LANGUAGE + " = '" + language.name() + "'"
                + " AND " + KEY_FILE_NAME + " = '" + fileName + "'";
        Log.v(TAG, "getFile started, query: " + select);
        SQLiteDatabase db = this.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            Log.v(TAG, "getReadableDatabase() executed");
            Cursor c = db.rawQuery(select, null);
            if (c.moveToFirst()) result = c.getString(0);
            c.close();
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }
        Log.v(TAG, "getFile finished");
        return result;
    }

    public boolean isBibleInDatabase(Language language) {
        String select = "SELECT COUNT(*)"
                + " FROM " + TABLE_NAME
                + " WHERE " + KEY_LANGUAGE + " = '" + language.name() + "'";
        int result = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            Log.v(TAG, "getReadableDatabase() executed");
            Cursor c = db.rawQuery(select, null);
            if (c.moveToFirst()) result = c.getInt(0);
            c.close();
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }
        Log.v(TAG, "CHAPTERS_IN_BIBLE of this language: " + String.valueOf(result));
        return (result == EXPECTED_CHAPTERS_IN_BIBLE);
    }

    public void insertFilesFromFolder(File folder, Language language) {
        try {
            FileFilter fileFilter = new RegexFileFilter(fileRegexMap.get(language));
            File[] fileList = folder.listFiles(fileFilter);
            String xhtml;

            SQLiteDatabase db = getWritableDatabase();
            SQLiteStatement insStmt = db.compileStatement("INSERT INTO " + TABLE_NAME + " " +
                    "(" + KEY_LANGUAGE + ", " + KEY_FILE_NAME + ", " + KEY_CONTENTS + ") VALUES (?, ?, ?);");
            db.beginTransactionNonExclusive();
            try {
                for (File file : fileList) {
                    InputStream is = new FileInputStream(file);
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    is.read(buffer);
                    is.close();
                    xhtml = new String(buffer);
                    insStmt.bindString(1, language.name());
                    insStmt.bindString(2, file.getName());
                    insStmt.bindString(3, xhtml);
                    insStmt.executeInsert();
                }
                db.setTransactionSuccessful();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
                db.close();
            }
            Log.v(TAG, "all filtered files inserted to db");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteAll() {
        try {
            Log.v(TAG, "deleteAll started");
            SQLiteDatabase db = getWritableDatabase();
            SQLiteStatement delStmt = db.compileStatement("DELETE FROM " + TABLE_NAME + ";");
            delStmt.execute();
            db.close();
            Log.v(TAG, "deleteAll ended");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteLanguage(Language language) {
        Log.v(TAG, "delete language started");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            SQLiteStatement delStmt = db.compileStatement("DELETE FROM " + TABLE_NAME
                    + " WHERE " + KEY_LANGUAGE + " = '" + language.name() + "'");
            delStmt.execute();
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }
        Log.v(TAG, "delete language ended");
    }

    public void exportDb() {
        File backupDB = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "database_backup.db");
        backupDB.delete();
        File currentDB = context.getDatabasePath(DATABASE_NAME);
        try {
            Log.v(TAG, "db copy start, path: " + backupDB.getAbsolutePath());
            if (currentDB.exists()) {
                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            }
            MediaScannerConnection.scanFile(context, new String[]{backupDB.getAbsolutePath()}, null, null);
            Log.v(TAG, "db copy end");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
