package org.bittwiddlers.wallpaperanimator;

import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
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
        private final Handler mHandler = new Handler();
        private final Runnable mUpdateDisplay = new Runnable() {
            public void run() {
                draw();
            }
        };
        private boolean mVisible = false;
        private Rect animSourceRect = null;
        private RectF targetRect = null;
        private Bitmap commonBitmap = null;
        private BitmapFactory.Options commonOptions = null;
        private boolean loaded = false;
        private String folderPath = null;
        private Pattern rgxExtract = null;
        private int screenWidth;
        private int screenHeight;
        private int xPixelOffset;
        private float xOffsetStep;
        private float xOffset;
        private int frame;
        private String[] framePaths;

        public AnimateEngine() {
            folderPath = Environment.getExternalStorageDirectory() + "/animated-wallpaper/";

            rgxExtract = java.util.regex.Pattern.compile(".*?([0-9]++)(?:\\..+)+$", Pattern.CASE_INSENSITIVE);
        }

        private boolean tryLoadFrames() {
            // Don't reload if we already loaded:
            if (loaded)
                return true;

            // Set up options to only decode bounds of bitmaps and not to
            // allocate pixel memory:
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            File[] list;
            try {
                Log.d("tryLoadFrames", String.format("Searching directory '%s'", folderPath));

                File folder = new File(folderPath);
                list = folder.listFiles();
            } catch (Throwable e) {
                Log.e("tryLoadFrames", "Search directory", e);
                return false;
            }

            SortedMap<String, String> frames = new TreeMap<String, String>();
            for (File f : list) {
                if (f.isDirectory())
                    continue;

                String name = f.getName();

                // Parse out the layer and frame number:
                Matcher m = rgxExtract.matcher(name);
                if (!m.matches()) {
                    Log.d("tryLoadFrames", String.format("Skipping non-matching filename '%s'", name));
                    continue;
                }

                String frameNo = m.group(1);

                // Prevent duplicates, if at all possible:
                if (frames.containsKey(frameNo)) {
                    Log.w("tryLoadFrames", String.format("Skipping frame %s; already loaded", frameNo));
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
                                String.format("Skipping frame %s due to mismatched width (%d != %d) or height (%d != %d)", frameNo,
                                        size.width(), animSourceRect.width(), size.height(), animSourceRect.height()));
                        continue;
                    }
                }

                // Add the frame to the layer's set:
                frames.put(frameNo, f.getAbsolutePath());
            }

            // Copy the paths to the array (assuming values() is sorted here):
            Log.d("tryLoadFrames", String.format("%d frames", frames.size()));
            if (frames.size() == 0) {
                framePaths = null;
                return false;
            }

            frame = 0;
            framePaths = frames.values().toArray(new String[frames.size()]);

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
            screenWidth = width;
            screenHeight = height;

            setupInitialFrame();
            draw();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            this.xPixelOffset = xPixelOffset;
            this.xOffsetStep = xOffsetStep;
            this.xOffset = xOffset;
            targetRect = null;

            // TODO Auto-generated method stub
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
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

        private void draw() {
            final boolean isLoaded = loaded;
            final boolean isVisible = mVisible;

            if (!isLoaded)
                return;

            SurfaceHolder holder;
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
                        render(c);
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
                // Log.d("draw", String.format("Frame time %d ms", frameTime));

                long delay = 33 - frameTime;
                if (delay < 20l)
                    delay = 20l;

                mHandler.postDelayed(mUpdateDisplay, delay);
            }
        }

        private void render(Canvas c) {
            // Clear the screen:
            c.drawColor(Color.BLACK);

            final float cw = screenWidth, ch = screenHeight;

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
                final float cr = cw / ch;
                final float fr = (float) fw / (float) fh;
                final float ew = Math.min(fr - cr, cr - fr);
                final float xscale = 1f - ew;
                final float yscale = ((ew > 0.01f) || (ew < -0.01f))
                        ? 1f
                        : (cw / (float)fw);

                final float w = fw * xscale * yscale;
                final float left = xOffset * (fw * ew);

                final float top = ch * 0.5f;
                targetRect = new RectF(left, top - (fh * xscale * yscale * 0.5f), left + w - 1, top + (fh * xscale * yscale * 0.5f) - 1);
                //Log.d("draw", String.format("left = %f, w = %f, cr = %f, fr = %f, ew = %f", left, w, cr, fr, ew));
            }

            if (targetRect == null || animSourceRect == null || commonBitmap == null)
                return;

            // Prevent index out of range error:
            if (frame >= framePaths.length)
                frame = 0;
            if (frame >= framePaths.length)
                return;

            try {
                // Draw the bitmap:
                String path = framePaths[frame];

                Bitmap bm = BitmapFactory.decodeFile(path, commonOptions);
                if (bm != null) {
                    c.drawBitmap(bm, animSourceRect, targetRect, null);
                }
                bm = null;
            } catch (Throwable e) {
                Log.e("draw", "Render bitmap", e);
            }

            // Increment layer's frame counter:
            frame = frame + 1;
            if (frame >= framePaths.length)
                frame = 0;
        }
    }
}
