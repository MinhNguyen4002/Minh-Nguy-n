package msv22b1001p0268.nguyenhongminh.myappmusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SeekBar
        .OnSeekBarChangeListener {
    private static final int REQUEST_CODE_PERMISSION = 1001;
    private static final int LEVEL_PAUSE = 0;
    private static final int LEVEL_PLAY = 1;
    private static final MediaPlayer player = new MediaPlayer();
    private static final int STATE_IDLE = 1;
    private static final int STATE_PLAYING = 2;
    private static final int STATE_PAUSED = 3;

    private final ArrayList<SongEntity> listSong = new ArrayList<>();
    private TextView tvName, tvAlbum, tvTime;
    private SeekBar seekBar;
    private ImageView ivPlay;
    private int index = 0;
    private SongEntity songEntity;
    private Thread thread;
    private int state = STATE_IDLE;
    private String totalTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initViews();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_CODE_PERMISSION);
            } else {
                loadingListSongOffline();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSION);
            } else {
                loadingListSongOffline();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void initViews() {
        ivPlay = findViewById(R.id.iv_play);
        ivPlay.setOnClickListener(this);
        findViewById(R.id.iv_back).setOnClickListener(this);
        findViewById(R.id.iv_next).setOnClickListener(this);
        tvName = findViewById(R.id.tv_name);
        tvAlbum = findViewById(R.id.tv_album);
        tvTime = findViewById(R.id.tv_time);
        seekBar = findViewById(R.id.seekbar);
        seekBar.setOnSeekBarChangeListener(this);
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 1001);
            return;
        }
        loadingListSongOffline();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadingListSongOffline();
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền để sử dụng ứng dụng!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private void loadingListSongOffline() {
        Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, MediaStore.Audio.Media.IS_MUSIC + "!= 0", null, null);
        if (c != null) {
            listSong.clear();
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String path = c.getString(c.getColumnIndex(MediaStore.Audio.Media.DATA));
                String album = c.getString(c.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                listSong.add(new SongEntity(name, path, album));
            }
            c.close();
        }
        if (listSong.isEmpty()) {
            Toast.makeText(this, "Danh sách bài hát trống", Toast.LENGTH_SHORT).show();
            Log.e("LoadingSongs", "Không tìm thấy bài hát nào trong thiết bị!");
        }
        updateRecyclerView();
    }

    private void updateRecyclerView() {RecyclerView rv = findViewById(R.id.rv_song);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new MusicAdapter(listSong, this));

    }


    private void playPause() {
        if (state == STATE_PLAYING && player.isPlaying()) {
            player.pause();
            ivPlay.setImageLevel(LEVEL_PAUSE);
            state = STATE_PAUSED;
        } else if (state == STATE_PAUSED) {
            player.start();
            state = STATE_PLAYING;
            ivPlay.setImageLevel(LEVEL_PLAY);
        } else {
            play();
        }
    }

    private void play() {
        if (listSong.isEmpty()) {
            Toast.makeText(this, "Danh sách bài hát trống", Toast.LENGTH_SHORT).show();
            return;
        }

        if (index < 0 || index >= listSong.size()) {
            Toast.makeText(this, "Chỉ số bài hát không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        songEntity = listSong.get(index);
        tvName.setText(songEntity.getName());
        tvAlbum.setText(songEntity.getAlbum());
        player.reset();

        try {
            player.setDataSource(songEntity.getPath());
            player.prepare();
            player.start();
            ivPlay.setImageLevel(LEVEL_PLAY);
            state = STATE_PLAYING;
            totalTime = getTime(player.getDuration());
            seekBar.setMax(player.getDuration());
            if (thread == null) {
                startLooping();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể phát bài hát", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLooping() {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            return;
                        }
                        runOnUiThread(() -> updateTime());
                    }
                }
            };
            thread.start();
        }
    }


    public void playSong(SongEntity songEntity) {
        index = listSong.indexOf(songEntity);
        this.songEntity = songEntity;
        play();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.iv_play) {
            playPause();
        } else if (v.getId() == R.id.iv_next) {
            next();
        } else if (v.getId() == R.id.iv_back) {
            back();
        }
    }

    private void back() {
        if (!listSong.isEmpty()) {
            index = (index - 1 + listSong.size()) % listSong.size();
            play();
        }
    }

    private void next() {
        if (!listSong.isEmpty()) {
            index = (index + 1) % listSong.size();
            play();
        }
    }
    private void updateTime() {
        if (state == STATE_PLAYING || state == STATE_PAUSED) {
            int time = player.getCurrentPosition();
            tvTime.setText(String.format("%s/%s", getTime(time), totalTime));
            seekBar.setProgress(time);
        }
    }
    @SuppressLint("SimpleDateFormat")
    private String getTime(int time) {
        return new SimpleDateFormat("mm:ss").format(new Date(time));
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        if (player != null) {
            player.release();
        }
    }
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && (state == STATE_PLAYING || state == STATE_PAUSED)) {
            player.seekTo(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (state == STATE_PLAYING || state == STATE_PAUSED) {
            player.seekTo(seekBar.getProgress());
        }
    }

}