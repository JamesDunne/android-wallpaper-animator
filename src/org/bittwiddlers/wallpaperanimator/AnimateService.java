package org.bittwiddlers.wallpaperanimator;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnimateService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        // TODO Auto-generated method stub
        return new AnimateEngine();
    }

    private class AnimateEngine extends Engine {
        private boolean        mVisible       = false;
        private final Handler  mHandler       = new Handler();
        private final Runnable mUpdateDisplay = new Runnable() {
                                                  public void run() {
                                                      draw();
                                                  }
                                              };

        private Rect           animSourceRect = null;

        private class LayerInfo {
            public int      frame;
            public String[] framePaths;
        }

        private LayerInfo[]           layers        = null;
        private RectF                 targetRect    = null;
        private Bitmap                commonBitmap  = null;
        private BitmapFactory.Options commonOptions = null;

        private boolean               loaded        = false;

        private String                folderPath    = null;
        private Pattern               rgxExtract    = null;

        public AnimateEngine() {
            folderPath = Environment.getExternalStorageDirectory() + "/animated-wallpaper/";

            rgxExtract = java.util.regex.Pattern.compile("layer(?:.*?)([0-9]++)_frame(?:.*?)([0-9]++)(?:\\..+)*$", Pattern.CASE_INSENSITIVE);
        }

        private final boolean tryLoadFrames() {
            // Don't reload if we already loaded:
            if (loaded)
                return true;

            // Set up options to only decode bounds of bitmaps and not to
            // allocate pixel memory:
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            SortedMap<String, SortedMap<String, String>> layerSet = new TreeMap<String, SortedMap<String, String>>();
            try {
                Log.i("tryLoadFrames", String.format("Searching directory '%s'", folderPath));

                File folder = new File(folderPath);
                File[] list = folder.listFiles();
                for (int i = 0; i < list.length; ++i) {
                    File f = list[i];
                    if (f.isDirectory())
                        continue;

                    String name = f.getName();

                    // Parse out the layer and frame number:
                    Matcher m = rgxExtract.matcher(name);
                    if (!m.matches()) {
                        Log.v("tryLoadFrames", String.format("Skipping non-matching filename '%s'", name));
                        continue;
                    }

                    String layer = m.group(1);
                    String frame = m.group(2);

                    SortedMap<String, String> set;
                    if (layerSet.containsKey(layer)) {
                        set = layerSet.get(layer);
                    } else {
                        set = new TreeMap<String, String>();
                        layerSet.put(layer, set);
                    }

                    // Prevent duplicates, if at all possible:
                    if (set.containsKey(frame)) {
                        Log.w("tryLoadFrames", String.format("Skipping layer %s frame %s; already loaded", layer, frame));
                        continue;
                    }

                    // Only parse out the dimensions of the bitmap:
                    try {
                        Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
                        assert bm == null;
                    } catch (Throwable e) {
                        Log.e("tryLoadFrames", "decodeFile", e);
                        continue;
                    }

                    // Validate the frame's dimensions:
                    Rect size = new Rect(0, 0, options.outWidth, options.outHeight);
                    if (animSourceRect == null) {
                        // Record the first frame's width and height:
                        animSourceRect = size;
                    } else {
                        // Reject frames that are not the same size as the first
                        // frame:
                        if (!animSourceRect.equals(size)) {
                            Log.w("tryLoadFrames",
                                    String.format("Skipping layer %s frame %s due to mismatched width (%d != %d) or height (%d != %d)", layer, frame,
                                            size.width(), animSourceRect.width(), size.height(), animSourceRect.height()));
                            continue;
                        }
                    }

                    // Add the frame to the layer's set:
                    set.put(frame, f.getAbsolutePath());
                }
            } catch (Throwable e) {
                Log.e("tryLoadFrames", "Body", e);
                return false;
            }

            try {
                // No layers loaded?
                if (layerSet.size() == 0) {
                    layers = null;
                } else {
                    // Copy the paths to the array (assuming values() is sorted
                    // here):
                    Log.v("tryLoadFrames", String.format("%d layers", layerSet.size()));
                    layers = new LayerInfo[layerSet.size()];

                    int i = 0;
                    for (Iterator<String> it = layerSet.keySet().iterator(); it.hasNext(); ++i) {
                        String layer = it.next();

                        Collection<String> frames = layerSet.get(layer).values();
                        Log.v("tryLoadFrames", String.format("Layer %s has %d frames", layer, frames.size()));

                        if (frames.size() == 0) {
                            layers[i] = null;
                            continue;
                        }

                        layers[i] = new LayerInfo();
                        layers[i].frame = 0;
                        layers[i].framePaths = frames.toArray(new String[frames.size()]);
                    }
                }
            } catch (Throwable e) {
                Log.e("tryLoadFrames", "layerSet", e);
                return false;
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

            targetRect = null;
            commonBitmap = null;
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
            mHandler.removeCallbacks(mUpdateDisplay);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mVisible = false;
            mHandler.removeCallbacks(mUpdateDisplay);
        }

        private final void draw() {
            final boolean isLoaded = loaded;
            boolean isVisible = mVisible;

            if (!isLoaded) {
                isVisible = false;
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
                        // Clear the screen:
                        c.drawColor(Color.BLACK);

                        if (isVisible) {
                            final int cw = c.getWidth(), ch = c.getHeight();

                            if (targetRect == null && animSourceRect != null) {
                                final int fw = animSourceRect.width(), fh = animSourceRect.height();
                                if (commonBitmap == null) {
                                    commonBitmap = Bitmap.createBitmap(fw, fh, Config.ARGB_8888);

                                    commonOptions = new BitmapFactory.Options();
                                    commonOptions.inBitmap = commonBitmap;
                                    commonOptions.inSampleSize = 1;
                                    commonOptions.inMutable = true;
                                    commonOptions.inScaled = false;
                                }

                                // Scales bitmap up and centers it:
                                float scale;
                                scale = Math.min((float) ch / (float) fh, (float) cw / (float) fw);

                                final float halfwidth = fw * scale * 0.5f, halfheight = fh * scale * 0.5f;
                                final float centerX = cw * 0.5f, centerY = ch * 0.5f;
                                targetRect = new RectF(centerX - halfwidth, centerY - halfheight, centerX + halfwidth, centerY + halfheight);
                            }

                            if (targetRect != null && commonBitmap != null) {
                                // Draw layers in order:
                                for (int i = 0; i < layers.length; ++i) {
                                    LayerInfo layer = layers[i];
                                    if (layer == null)
                                        continue;

                                    // Prevent index out of range error:
                                    if (layer.frame >= layer.framePaths.length)
                                        layer.frame = 0;
                                    if (layer.frame >= layer.framePaths.length)
                                        continue;

                                    try {
                                        // Draw the bitmap:
                                        String path = layer.framePaths[layer.frame];

                                        Bitmap bm = BitmapFactory.decodeFile(path, commonOptions);
                                        if (bm != null) {
                                            //c.drawBitmap(bm, 0f, 0f, null);
                                            c.drawBitmap(bm, animSourceRect, targetRect, null);
                                        }
                                        bm = null;
                                    } catch (Throwable e) {
                                        Log.e("draw", "Render bitmap", e);
                                    }

                                    // Increment layer's frame counter:
                                    layer.frame = layer.frame + 1;
                                    if (layer.frame >= layer.framePaths.length)
                                        layer.frame = 0;
                                }
                            }
                        }
                    }
                }
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Throwable e) {
                        Log.e("draw", "unlockCanvasAndPost", e);
                    }
                }
            }

            mHandler.removeCallbacks(mUpdateDisplay);
            if (isVisible) {
                // 30 fps = 33.3333ms per frame
                long frameTime = (android.os.SystemClock.uptimeMillis() - startTime);
                Log.v("draw", String.format("Frame time %d ms", frameTime));

                long delay = 33 - frameTime;
                if (delay < 20l)
                    delay = 20l;

                mHandler.postDelayed(mUpdateDisplay, delay);
            }
        }
    }
}
