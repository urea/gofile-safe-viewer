package jp.urea.gofilesafeviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Native bookmark list. Remote pages cannot read or modify this store. */
@SuppressLint("SetTextI18n")
public final class BookmarkListActivity extends Activity {
    private BookmarkStore bookmarks;
    private LinearLayout listContainer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bookmarks = new BookmarkStore(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        UiChrome.showSystemBars(this);
        renderBookmarks();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiChrome.showSystemBars(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(20));
        scroll.addView(page);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ブックマーク", 22);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = new Button(this);
        home.setText("ホーム");
        home.setOnClickListener(v -> goHome());
        heading.addView(home, new LinearLayout.LayoutParams(dp(88), -2));
        page.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        page.addView(text("保存したURLはこの端末内だけに保持します。タップすると安全ビューアで開きます。", 13));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(listContainer, new LinearLayout.LayoutParams(-1, -2));
        UiChrome.setContentView(this, scroll);
    }

    private void renderBookmarks() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        List<String> saved = bookmarks.list();
        if (saved.isEmpty()) {
            TextView empty = text("ブックマークはありません。閲覧画面の「☆保存」から追加できます。", 14);
            empty.setPadding(0, dp(16), 0, dp(8));
            listContainer.addView(empty);
            return;
        }
        for (String url : saved) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            Button open = new Button(this);
            open.setAllCaps(false);
            open.setText(url);
            open.setTextSize(12);
            open.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            open.setMaxLines(2);
            open.setEllipsize(TextUtils.TruncateAt.END);
            open.setOnClickListener(v -> openUrl(url));
            LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(64), 1);
            openParams.setMarginEnd(dp(6));
            row.addView(open, openParams);

            Button remove = new Button(this);
            remove.setText("削除");
            remove.setTextSize(13);
            remove.setOnClickListener(v -> {
                bookmarks.remove(url);
                Toast.makeText(this, "ブックマークを削除しました。", Toast.LENGTH_SHORT).show();
                renderBookmarks();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(56)));
            listContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(this, SafeActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(intent);
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 43, 51));
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }
}
