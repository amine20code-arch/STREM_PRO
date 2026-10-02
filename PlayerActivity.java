package dz.stream.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

/** مشغّل ExoPlayer لروابط TS / MKV / MP4 وغيرها. ↑↓ = القناة السابقة/التالية، OK = إيقاف/تشغيل، ←→ = ±10 ثوانٍ */
@UnstableApi
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private TextView title;
    private final Runnable hideTitle = new Runnable() {
        @Override
        public void run() {
            title.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("title");
        if (url == null) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView pv = new PlayerView(this);
        pv.setUseController(false);
        root.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0x99000000);
        title.setPadding(24, 12, 24, 12);
        title.setTextSize(18);
        title.setText(name == null ? "" : name);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.setMargins(32, 32, 32, 0);
        root.addView(title, lp);
        setContentView(root);
        showTitle();

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        pv.setPlayer(player);

        MediaItem.Builder mb = new MediaItem.Builder().setUri(url);
        if (url.toLowerCase().contains("m3u8")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(mb.build());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                Toast.makeText(PlayerActivity.this, "تعذر التشغيل: " + e.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }
        });
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void showTitle() {
        title.setVisibility(View.VISIBLE);
        title.removeCallbacks(hideTitle);
        title.postDelayed(hideTitle, 4000);
    }

    private void zap(int n) {
        Intent r = new Intent();
        r.putExtra("zap", n);
        setResult(RESULT_OK, r);
        finish();
    }

    private void seek(long ms) {
        if (player != null && player.isCurrentMediaItemSeekable()) {
            player.seekTo(Math.max(0, player.getCurrentPosition() + ms));
            showTitle();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
                showTitle();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(10000);
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (title != null) title.removeCallbacks(hideTitle);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
