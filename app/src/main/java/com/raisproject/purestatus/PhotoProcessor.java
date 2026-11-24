package com.raisproject.purestatus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** HD photo export (1080x1920 JPEG) + photo-to-5s-video (H.264, MediaCodec, no audio). */
public class PhotoProcessor {

    /** Scale/crop-centre the photo to exactly 1080x1920 and save as JPEG q95. */
    public static File exportHdPhoto(Context context, Uri input, File outDir) throws Exception {
        if (!outDir.exists()) outDir.mkdirs();
        Bitmap src;
        try (InputStream in = context.getContentResolver().openInputStream(input)) {
            src = BitmapFactory.decodeStream(in);
        }
        if (src == null) throw new IllegalStateException("Foto tidak bisa dibaca");
        Bitmap scaled = scaleCrop(src, 1080, 1920);
        if (scaled != src) src.recycle();
        File out = new File(outDir, "purestatus_photo_1080p.jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, 95, fos);
        }
        scaled.recycle();
        return out;
    }

    /** Make a 5-second 1080x1920 30fps video from the photo (same frame, YUV420 once). */
    public static File photoToVideo(Context context, Uri input, File outDir) throws Exception {
        if (!outDir.exists()) outDir.mkdirs();
        Bitmap src;
        try (InputStream in = context.getContentResolver().openInputStream(input)) {
            src = BitmapFactory.decodeStream(in);
        }
        if (src == null) throw new IllegalStateException("Foto tidak bisa dibaca");
        Bitmap scaled = scaleCrop(src, 1080, 1920);
        if (scaled != src) src.recycle();

        byte[] yuv = bitmapToI420(scaled);
        scaled.recycle();

        File out = new File(outDir, "purestatus_photo_5s.mp4");
        if (out.exists()) out.delete();
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, 1080, 1920);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int track = -1;
        boolean muxerStarted = false;
        int frames = 5 * 30;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        for (int i = 0; i < frames; i++) {
            int inIndex = codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer buf = codec.getInputBuffer(inIndex);
                buf.clear();
                buf.put(yuv);
                codec.queueInputBuffer(inIndex, 0, yuv.length,
                        (long) i * 1_000_000L / 30, i == frames - 1
                                ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }
            int outIndex = codec.dequeueOutputBuffer(info, 10000);
            while (outIndex >= 0) {
                ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                    if (!muxerStarted) {
                        track = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    }
                    outBuf.position(info.offset);
                    outBuf.limit(info.offset + info.size);
                    muxer.writeSampleData(track, outBuf, info);
                }
                codec.releaseOutputBuffer(outIndex, false);
                outIndex = codec.dequeueOutputBuffer(info, 0);
            }
        }
        // Drain remaining output.
        int outIndex = codec.dequeueOutputBuffer(info, 10000);
        while (outIndex >= 0) {
            ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                outBuf.position(info.offset);
                outBuf.limit(info.offset + info.size);
                muxer.writeSampleData(track, outBuf, info);
            }
            codec.releaseOutputBuffer(outIndex, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            outIndex = codec.dequeueOutputBuffer(info, 10000);
        }
        codec.stop();
        codec.release();
        if (muxerStarted) muxer.stop();
        muxer.release();
        if (!out.exists() || out.length() == 0) {
            throw new IllegalStateException("Video foto kosong");
        }
        return out;
    }

    private static Bitmap scaleCrop(Bitmap src, int w, int h) {
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int sw = Math.round(src.getWidth() * scale);
        int sh = Math.round(src.getHeight() * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(src, sw, sh, true);
        int x = Math.max(0, (sw - w) / 2);
        int y = Math.max(0, (sh - h) / 2);
        Bitmap cropped = Bitmap.createBitmap(scaled, x, y, w, h);
        if (cropped != scaled) scaled.recycle();
        return cropped;
    }

    /** ARGB -> I420 (YUV420 planar). Converted once, reused for every frame. */
    private static byte[] bitmapToI420(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] argb = new int[w * h];
        bmp.getPixels(argb, 0, w, 0, 0, w, h);
        byte[] yuv = new byte[w * h * 3 / 2];
        int yIndex = 0;
        int uIndex = w * h;
        int vIndex = w * h + (w * h) / 4;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int rgb = argb[j * w + i];
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    yuv[uIndex++] = (byte) Math.max(0, Math.min(255, u));
                    yuv[vIndex++] = (byte) Math.max(0, Math.min(255, v));
                }
            }
        }
        return yuv;
    }
}
