package com.raisproject.purestatus;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Split + transcode a video to 1080x1920 30fps H.264/AAC with Media3 Transformer. */
public class VideoProcessor {

    public interface ProgressCallback {
        void onPartDone(int done, int total, File file);
        void onStatus(String text);
    }

    public static List<File> process(Context context, Uri input, long durationMs,
                                     int segmentSec, String tag, File outDir,
                                     ProgressCallback cb) throws Exception {
        if (!outDir.exists()) outDir.mkdirs();
        int total = (int) Math.max(1, Math.ceil(durationMs / (segmentSec * 1000.0)));
        List<File> out = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            long startMs = (long) i * segmentSec * 1000L;
            long endMs = Math.min((long) (i + 1) * segmentSec * 1000L, durationMs);
            File outFile = new File(outDir, "purestatus_" + tag + "_part" + (i + 1) + ".mp4");
            if (outFile.exists()) outFile.delete();
            if (cb != null) cb.onStatus("Proses bagian " + (i + 1) + "/" + total + " (" + tag + ")...");
            transcodeOne(context, input, startMs, endMs, outFile);
            out.add(outFile);
            if (cb != null) cb.onPartDone(i + 1, total, outFile);
        }
        return out;
    }

    private static void transcodeOne(Context context, Uri input, long startMs, long endMs,
                                     File outFile) throws Exception {
        MediaItem.ClippingConfiguration clipping =
                new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build();
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(input)
                .setClippingConfiguration(clipping)
                .build();
        // 1080x1920 vertical, crop-to-fill so Status is full-screen, no black bars.
        Presentation presentation = Presentation.createForWidthAndHeight(
                1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP);
        Effects effects = new Effects(
                ImmutableList.of(),
                ImmutableList.of(presentation));
        EditedMediaItem edited = new EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        Transformer.Listener listener = new Transformer.Listener() {
            @Override
            public void onCompleted(Composition composition, ExportResult result) {
                latch.countDown();
            }

            @Override
            public void onError(Composition composition, ExportResult result,
                                ExportException exception) {
                error.set(exception);
                latch.countDown();
            }
        };
        Transformer transformer = new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build();
        transformer.start(edited, outFile.getAbsolutePath());
        latch.await();
        if (error.get() != null) throw error.get();
        if (!outFile.exists() || outFile.length() == 0) {
            throw new IllegalStateException("Output kosong: " + outFile.getName());
        }
    }
}
