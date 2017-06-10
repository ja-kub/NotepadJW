package pl.bubson.notepadjw.utils;

import android.Manifest;

/**
 * Created by Kuba on 2017-03-28.
 */
public class Permissions {
    public static final int MY_REQUEST_PERMISSIONS_CODE = 1;
    public static String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
    };
    public static final String WRITE_EXTERNAL_STORAGE = "user_acceptation_write_external_storage";
    public static final int NOT_ANSWERED_YET = 0;
    public static final int ACCEPTED = 1;
    public static final int DENIED = 2;
}
