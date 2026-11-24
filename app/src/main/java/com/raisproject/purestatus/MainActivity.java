package com.raisproject.purestatus;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SEG_WA = 60;
    private static final int SEG_IG = 60;
    private static final int SEG_FB = 20;
    private static final int SEG_REELS = 90;

    private Uri pickedUri;
    private boolean pickedIsVideo;
    private long videoDurationMs;
    private int segmentSec = SEG_WA;
    private String platformTag = "wa";
    private final List<File> results = new ArrayList<>();

    private TextView txtStatus, txtInfo, txtResults;
    private ProgressBar progress;
    private Button btnProcess, btnShare;
    private Button btnWa, btnIg, btnFb, btnReels;
    private CheckBox chkPhotoVideo;
    private VideoView videoPreview;
    private ImageView imgPreview;

    private final ActivityResultLauncher<String> pickVideo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                pickedUri = uri;
                pickedIsVideo = true;
                videoDurationMs = getDuration(uri);
                imgPreview.setVisibility(View.GONE);
                videoPreview.setVisibility(View.VISIBLE);
                videoPreview.setVideoURI(uri);
                videoPreview.start();
                txtInfo.setText("Video dipilih — durasi " + (videoDurationMs / 1000) + " detik\n"
                        + "Akan di-split per " + segmentSec + " detik, output 1080x1920 30fps.");
                btnProcess.setEnabled(true);
            });

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                pickedUri = uri;
                pickedIsVideo = false;
                videoPreview.setVisibility(View.GONE);
                imgPreview.setVisibility(View.VISIBLE);
                imgPreview.setImageURI(uri);
                txtInfo.setText("Foto dipilih — akan diekspor HD 1080x1920 (JPEG q95).\n"
                        + "Centang 'Foto jadi video 5 detik' kalau mau kirim sebagai Status video.");
                btnProcess.setEnabled(true);
            });

    private final ActivityResultLauncher<String> requestPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (!ok) Toast.makeText(this, "Izin galeri dibutuhkan", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        txtInfo = findViewById(R.id.txtInfo);
        txtResults = findViewById(R.id.txtResults);
        progress = findViewById(R.id.progress);
        btnProcess = findViewById(R.id.btnProcess);
        btnShare = findViewById(R.id.btnShare);
        btnWa = findViewById(R.id.btnWa);
        btnIg = findViewById(R.id.btnIg);
        btnFb = findViewById(R.id.btnFb);
        btnReels = findViewById(R.id.btnReels);
        chkPhotoVideo = findViewById(R.id.chkPhotoVideo);
        videoPreview = findViewById(R.id.videoPreview);
        imgPreview = findViewById(R.id.imgPreview);

        findViewById(R.id.btnPickVideo).setOnClickListener(v -> {
            ensurePermission();
            pickVideo.launch("video/*");
        });
        findViewById(R.id.btnPickPhoto).setOnClickListener(v -> {
            ensurePermission();
            pickPhoto.launch("image/*");
        });

        btnWa.setOnClickListener(v -> selectPlatform(SEG_WA, "wa", btnWa));
        btnIg.setOnClickListener(v -> selectPlatform(SEG_IG, "ig", btnIg));
        btnFb.setOnClickListener(v -> selectPlatform(SEG_FB, "fb", btnFb));
        btnReels.setOnClickListener(v -> selectPlatform(SEG_REELS, "reels", btnReels));
        selectPlatform(SEG_WA, "wa", btnWa);

        btnProcess.setOnClickListener(v -> startProcess());
        btnShare.setOnClickListener(v -> shareResults(null));
        findViewById(R.id.btnShareWa).setOnClickListener(v -> shareResults("com.whatsapp"));
        findViewById(R.id.btnShareIg).setOnClickListener(v -> shareResults("com.instagram.android"));
        findViewById(R.id.btnShareFb).setOnClickListener(v -> shareResults("com.facebook.katana"));
    }

    private void selectPlatform(int sec, String tag, Button active) {
        segmentSec = sec;
        platformTag = tag;
        for (Button b : new Button[]{btnWa, btnIg, btnFb, btnReels}) {
            b.setAlpha(0.55f);
        }
        active.setAlpha(1f);
        txtStatus.setText(platformLabel(tag) + " — split " + sec + " detik/part, output 1080p");
    }

    private String platformLabel(String tag) {
        switch (tag) {
            case "ig": return "IG Story";
            case "fb": return "FB Story";
            case "reels": return "Reels IG/FB";
            default: return "WA Status";
        }
    }

    private void ensurePermission() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            requestPerm.launch(perm);
        }
    }

    private long getDuration(Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, uri);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0 : Long.parseLong(d);
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private void startProcess() {
        if (pickedUri == null) {
            Toast.makeText(this, "Pilih video / foto dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        btnProcess.setEnabled(false);
        btnShare.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        results.clear();
        txtResults.setText("");
        File outDir = new File(getExternalFilesDir(null), "PureStatus");
        new Thread(() -> {
            try {
                if (pickedIsVideo) {
                    if (videoDurationMs <= 0) videoDurationMs = getDuration(pickedUri);
                    List<File> files = VideoProcessor.process(this, pickedUri, videoDurationMs,
                            segmentSec, platformTag, outDir, new VideoProcessor.ProgressCallback() {
                                @Override public void onPartDone(int done, int total, File f) {
                                    runOnUiThread(() -> {
                                        progress.setProgress(done * 100 / total);
                                        txtStatus.setText("Selesai " + done + "/" + total);
                                    });
                                }
                                @Override public void onStatus(String t) {
                                    runOnUiThread(() -> txtStatus.setText(t));
                                }
                            });
                    results.addAll(files);
                } else {
                    setStatus("Ekspor foto HD 1080p...");
                    results.add(PhotoProcessor.exportHdPhoto(this, pickedUri, outDir));
                    if (chkPhotoVideo.isChecked()) {
                        setStatus("Buat video 5 detik dari foto...");
                        results.add(PhotoProcessor.photoToVideo(this, pickedUri, outDir));
                    }
                    runOnUiThread(() -> progress.setProgress(100));
                }
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnShare.setVisibility(View.VISIBLE);
                    StringBuilder sb = new StringBuilder("Hasil (" + results.size() + " file, 1080p, tanpa watermark):\n");
                    for (File f : results) sb.append("• ").append(f.getName())
                            .append(" — ").append(f.length() / 1024).append(" KB\n");
                    txtResults.setText(sb.toString());
                    txtStatus.setText("Selesai. Bagikan ke " + platformLabel(platformTag) + " di bawah.");
                    Toast.makeText(this, "Selesai — " + results.size() + " file", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    txtStatus.setText("Gagal: " + e.getMessage());
                    Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setStatus(String t) {
        runOnUiThread(() -> txtStatus.setText(t));
    }

    private void shareResults(String pkg) {
        if (results.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : results) {
            uris.add(FileProvider.getUriForFile(this,
                    "com.raisproject.purestatus.fileprovider", f));
        }
        Intent intent = new Intent(uris.size() > 1
                ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        intent.setType(pickedIsVideo ? "video/*" : "image/*");
        if (uris.size() > 1) intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        else intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (pkg != null) {
            intent.setPackage(pkg);
            try {
                startActivity(intent);
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Aplikasi tujuan belum terinstal", Toast.LENGTH_SHORT).show();
            }
        }
        startActivity(Intent.createChooser(intent, "Bagikan ke Status"));
    }
}
