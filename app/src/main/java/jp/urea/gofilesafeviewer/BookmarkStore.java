package jp.urea.gofilesafeviewer;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores user-created bookmark URLs locally on this device. */
final class BookmarkStore {
    private static final String PREFS = "gsv_bookmarks";
    private static final String KEY_URLS = "urls";
    private final SharedPreferences preferences;

    BookmarkStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<String> list() {
        Set<String> saved = preferences.getStringSet(KEY_URLS, Collections.emptySet());
        ArrayList<String> result = new ArrayList<>(saved == null ? Collections.emptySet() : saved);
        Collections.sort(result);
        return result;
    }

    boolean contains(String url) {
        if (url == null) return false;
        Set<String> saved = preferences.getStringSet(KEY_URLS, Collections.emptySet());
        return saved != null && saved.contains(url);
    }

    void add(String url) {
        if (url == null || url.trim().isEmpty()) return;
        Set<String> saved = mutableCopy();
        saved.add(url.trim());
        preferences.edit().putStringSet(KEY_URLS, saved).apply();
    }

    void remove(String url) {
        if (url == null) return;
        Set<String> saved = mutableCopy();
        if (saved.remove(url)) preferences.edit().putStringSet(KEY_URLS, saved).apply();
    }

    private Set<String> mutableCopy() {
        Set<String> saved = preferences.getStringSet(KEY_URLS, Collections.emptySet());
        return new HashSet<>(saved == null ? Collections.emptySet() : saved);
    }
}
