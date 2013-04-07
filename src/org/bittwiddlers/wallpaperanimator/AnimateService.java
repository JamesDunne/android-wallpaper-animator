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

import java.io.File;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        private boolean        loaded           = false;
        private String[]       layers           = null;
        private String[]       frames           = null;

        private String         folderPath       = null;
        private Pattern        imageNumberRegex = null;

        public AnimateEngine() {
            folderPath = Environment.getExternalStorageDirectory() + "/animated-wallpaper/";

            imageNumberRegex = java.util.regex.Pattern.compile(".*?([0-9]++)(?:\\..+)*$", Pattern.CASE_INSENSITIVE);
        }

        private final boolean tryLoadFrames() {
            // Don't reload if we already loaded:
            if (loaded)
                return true;

            // Set up options to only decode bounds of bitmaps and not to
            // allocate pixel memory:
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            SortedMap<String, String> layerSet = new java.util.TreeMap<String, String>();
            SortedMap<String, String> frameSet = new java.util.TreeMap<String, String>();
            try {
                Log.i("tryLoadFrames", String.format("Searching directory '%s'", folderPath));

                File folder = new File(folderPath);
                File[] list = folder.listFiles();
                for (int i = 0; i < list.length; ++i) {
                    File f = list[i];
                    if (f.isDirectory())
                        continue;

                    String name = f.getName();
                    // Parse out the frame number:
                    Matcher m = imageNumberRegex.matcher(name);
                    if (!m.matches()) {
                        Log.v("tryLoadFrames", String.format("Skipping non-matching filename '%s'", name));
                        continue;
                    }

                    SortedMap<String, String> set = frameSet;
                    String kind = "frame";

                    if (name.startsWith("layer")) {
                        set = layerSet;
                        kind = "layer";
                    }

                    String number = m.group(1);
                    if (set.containsKey(number)) {
                        Log.w("tryLoadFrames", String.format("Skipping %s %s; already loaded", kind, number));
                        continue;
                    }

                    // Only parse out the dimensions of the bitmap:
                    Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
                    assert bm == null;

                    // Validate the frame's dimensions:
                    Rect size = new Rect(0, 0, options.outWidth, options.outHeight);
                    if (animSourceRect == null) {
                        // Record the first frame's width and height:
                        animSourceRect = size;
                    } else {
                        // Reject frames that are not the same size as the first
                        // frame:
                        if (!animSourceRect.equals(size)) {
                            Log.w("tryLoadFrames", String.format("Skipping %s %s due to mismatched width (%d != %d) or height (%d != %d)", kind, number,
                                    size.width(), animSourceRect.width(), size.height(), animSourceRect.height()));
                            continue;
                        }
                    }

                    // Add the image to the set:
                    set.put(number, f.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e("tryLoadFrames", "Body", e);
                return false;
            }

            // No layers loaded?
            if (layerSet.size() == 0) {
                layers = null;
            } else {
                // Copy the paths to the array (assuming values() is sorted
                // here):
                layers = new String[layerSet.size()];
                layerSet.values().toArray(layers);
            }

            // No frames loaded?
            if (frameSet.size() == 0) {
                frames = null;
            } else {
                // Copy the paths to the array (assuming values() is sorted
                // here):
                frames = new String[frameSet.size()];
                frameSet.values().toArray(frames);
            }

            return true;
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

        private void setupInitialFrame() {
            if (!loaded)
                loaded = tryLoadFrames();

            firstShow = true;
            frame = 0;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                setupInitialFrame();
                draw();
            } else {
                mHandler.removeCallbacks(mUpdateDisplay);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            setupInitialFrame();
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
            if (!loaded) {
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
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPurgeable = true;
                            options.inInputShareable = true;

                            final int cw = c.getWidth(), ch = c.getHeight();
                            final int fw = animSourceRect.width(), fh = animSourceRect.height();

                            // Scales bitmap up and centers it:
                            float scale;
                            scale = Math.min((float) ch / (float) fh, (float) cw / (float) fw);

                            final float halfwidth = fw * scale * 0.5f, halfheight = fh * scale * 0.5f;
                            final float centerX = cw / 2f, centerY = ch / 2f;
                            RectF target = new RectF(centerX - halfwidth, centerY - halfheight, centerX + halfwidth, centerY + halfheight);

                            Paint paint = new Paint();

                            if (layers != null) {
                                // Draw layers in order:
                                for (int i = 0; i < layers.length; ++i) {
                                    Bitmap bm = BitmapFactory.decodeFile(layers[i], options);
                                    c.drawBitmap(bm, animSourceRect, target, paint);
                                }
                            }
                            if (frames != null) {
                                // Draw the animation frame:
                                Bitmap bm = BitmapFactory.decodeFile(frames[frame], options);
                                c.drawBitmap(bm, animSourceRect, target, paint);

                                // Increment frame counter:
                                if (++frame >= frames.length)
                                    frame = 0;
                            }
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
