package jp.urea.gofilesafeviewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** A single read-only APK, exposed only through a temporary URI grant to Android's installer. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) {
        if (getContext() == null || !(getContext().getPackageName() + ".updates").equals(uri.getAuthority())
                || !"/update.apk".equals(uri.getEncodedPath()) || uri.getQuery() != null || uri.getFragment() != null) {
            throw new SecurityException("Unsupported update URI");
        }
        return new File(getContext().getCacheDir(), "updates/update.apk");
    }
    @Override public String getType(Uri uri) { file(uri); return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String sort) {
        File apk = file(uri);
        String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "GofileSafeViewer.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = apk.length();
        }
        cursor.addRow(row); return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new SecurityException("Read only"); }
}
