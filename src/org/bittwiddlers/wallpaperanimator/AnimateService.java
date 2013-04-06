package org.bittwiddlers.wallpaperanimator;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.FileInputStream;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AnimateService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        // TODO Auto-generated method stub
        return new AnimateEngine();
    }

    private class AnimateEngine extends Engine {
        private boolean        mVisible         = false;
        private final Handler  mHandler         = new Handler();
        private final Runnable mUpdateDisplay   = new Runnable() {
                                                    public void run() {
                                                        draw();
                                                    }
                                                };

        private Rect           animSourceRect   = null;

        private int            frame            = 0;
        private boolean        firstShow        = true;
        private Bitmap[]       frames           = null;

        private String         zipFile          = null;
        private Pattern        imageNumberRegex = null;

        public AnimateEngine() {
            zipFile = Environment.getExternalStorageDirectory() + "/animated-wallpaper.zip";

            imageNumberRegex = java.util.regex.Pattern.compile(".*?([0-9]++)(?:\\..+)*$", Pattern.CASE_INSENSITIVE);
        }

        private final boolean tryLoadFrames() {
            // Don't reload if we already loaded:
            if (frames != null)
                return true;

            // Load frames:
            frames = loadFramesZIP(zipFile, imageNumberRegex);
            if (frames == null)
                return false;

            return true;
        }

        private final Bitmap[] loadFramesZIP(String zipFile, Pattern imageNumberRegex) {
            SortedMap<String, Bitmap> frameSet = new java.util.TreeMap<String, Bitmap>();
            try {
                Log.v("loadFramesZIP", String.format("Opening %s", zipFile));

                FileInputStream fis = new FileInputStream(zipFile);
                ZipInputStream zis = new ZipInputStream(fis);

                ZipEntry ze = null;
                while ((ze = zis.getNextEntry()) != null) {
                    String name = ze.getName();

                    if (ze.isDirectory())
                        continue;

                    // Parse out the frame number:
                    Matcher m = imageNumberRegex.matcher(name);
                    if (!m.matches()) {
                        Log.v("loadFramesZIP", String.format("Skipping non-matching filename '%s'", name));
                        continue;
                    }

                    String number = m.group(1);
                    if (frameSet.containsKey(number)) {
                        Log.v("loadFramesZIP", String.format("Skipping frame %s; already loaded", number));
                        continue;
                    }

                    // Decode the bitmap from the ZIP stream:
                    Bitmap bm = BitmapFactory.decodeStream(zis);
                    zis.closeEntry();

                    // Validate the frame's dimensions:
                    Rect size = new Rect(0, 0, bm.getWidth(), bm.getHeight());
                    if (animSourceRect == null) {
                        // Record the first frame's width and height:
                        animSourceRect = size;
                    } else {
                        // Reject frames that are not the same size as the first
                        // frame:
                        if (!animSourceRect.equals(size)) {
                            Log.v("loadFramesZIP", String.format("Skipping frame %s due to mismatched width (%d != %d) or height (%d != %d)", number,
                                    size.width(), animSourceRect.width(), size.height(), animSourceRect.height()));
                            continue;
                        }
                    }

                    // Add the frame to the set:
                    frameSet.put(number, bm);
                }

                zis.close();
            } catch (Exception e) {
                Log.e("loadFramesZIP", zipFile, e);
                return null;
            }

            // No frames loaded?
            if (frameSet.size() == 0)
                return null;

            // Copy the Bitmaps to the array (assuming values() is sorted here):
            Bitmap[] frames = new Bitmap[frameSet.size()];
            frameSet.values().toArray(frames);

            return frames;
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            // // Handle tap commands eventually:
            // if (action == android.app.WallpaperManager.COMMAND_TAP) {
            // return null;
            // }
            // TODO Auto-generated method stub
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                if (frames == null)
                    tryLoadFrames();

                firstShow = true;
                frame = 0;

                draw();
            } else {
                mHandler.removeCallbacks(mUpdateDisplay);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (frames == null)
                tryLoadFrames();

            firstShow = true;
            frame = 0;

            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mVisible = false;
            firstShow = true;
            mHandler.removeCallbacks(mUpdateDisplay);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mVisible = false;
            firstShow = true;
            mHandler.removeCallbacks(mUpdateDisplay);
        }

        private final void draw() {
            if (frames == null) {
                mVisible = false;
            }

            SurfaceHolder holder = null;
            try {
                holder = getSurfaceHolder();
            } catch (Exception e) {
                Log.e("draw", "Could not getSurfaceHolder()", e);
                return;
            }

            Canvas c = null;
            long startTime = android.os.SystemClock.uptimeMillis();
            try {
                c = holder.lockCanvas(null);
                if (c != null) {
                    synchronized (holder) {
                        if (firstShow) {
                            // Clear the screen:
                            c.drawColor(Color.BLACK);
                            firstShow = false;
                        }

                        if (mVisible) {
                            // Render the next frame:
                            final int frameCount = frames.length;

                            final int cw = c.getWidth(), ch = c.getHeight();
                            final int fw = animSourceRect.width(), fh = animSourceRect.height();

                            // Scales bitmap up and centers it:
                            float scale;
                            scale = Math.min((float) ch / (float) fh, (float) cw / (float) fw);

                            final float halfwidth = fw * scale * 0.5f, halfheight = fh * scale * 0.5f;
                            final float centerX = cw / 2f, centerY = ch / 2f;

                            RectF target = new RectF(centerX - halfwidth, centerY - halfheight, centerX + halfwidth, centerY + halfheight);

                            // Draw the animation frame in the center:
                            Paint paint = new Paint();
                            c.drawBitmap(frames[frame], animSourceRect, target, paint);

                            // Increment frame counter:
                            if (++frame >= frameCount)
                                frame = 0;
                        }
                    }
                }
            } finally {
                if (c != null)
                    holder.unlockCanvasAndPost(c);
            }

            mHandler.removeCallbacks(mUpdateDisplay);
            if (mVisible) {
                // 30 fps = 33.3333ms per frame
                long delay = 33 - (android.os.SystemClock.uptimeMillis() - startTime);
                if (delay < 0l)
                    delay = 0l;

                mHandler.postDelayed(mUpdateDisplay, delay);
            }
        }
    }
}
