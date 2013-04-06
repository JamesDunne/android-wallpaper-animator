package org.bittwiddlers.wallpaperanimator;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
		private boolean mVisible = false;
		private final Handler mHandler = new Handler();
		private final Runnable mUpdateDisplay = new Runnable() {
			public void run() {
				draw();
			}
		};

		private int frame = 0;
		private int animWidth = 0, animHeight = 0;
		private Bitmap[] frames = null;

		public AnimateEngine() {
			tryLoadFrames();
		}

		private Boolean tryLoadFrames() {
			String zipFile = Environment.getExternalStorageDirectory()
					+ "/fixit.zip";

			Pattern imageNumberRegex = java.util.regex.Pattern
					.compile("n([0-9]+).png");

			frames = loadFramesZIP(zipFile, imageNumberRegex);
			if (frames == null)
				return false;

			return true;
		}

		private Bitmap[] loadFramesZIP(String zipFile, Pattern imageNumberRegex) {
			SortedMap<Integer, Bitmap> frameSet = new java.util.TreeMap<Integer, Bitmap>();
			try {
				FileInputStream fis = new FileInputStream(zipFile);
				ZipInputStream zis = new ZipInputStream(fis);

				ZipEntry ze = null;
				while ((ze = zis.getNextEntry()) != null) {
					String name = ze.getName();

					Log.v("loadFramesZIP", "At " + name);

					if (ze.isDirectory()) {
						Log.v("loadFramesZIP", "Skipping directory");
						continue;
					}

					Matcher m = imageNumberRegex.matcher(name);
					if (!m.matches()) {
						Log.v("loadFramesZIP", "Skipping non-matching filename");
						continue;
					}
					String number = m.group(1);
					Log.v("loadFramesZIP",
							String.format("Parsed frame number %d", number));

					int index = java.lang.Integer.parseInt(number, 10);
					if (frameSet.containsKey(index)) {
						Log.v("loadFramesZIP",
								String.format(
										"Skipping bitmap because frame number %d is already loaded",
										index));
						continue;
					}

					// Decode the bitmap from the ZIP stream:
					Bitmap bm = BitmapFactory.decodeStream(zis);
					zis.closeEntry();

					final int bmWidth = bm.getWidth();
					final int bmheight = bm.getHeight();

					if (animWidth == 0) {
						// Record the first frame's width and height:
						animWidth = bmWidth;
						animHeight = bmheight;
					} else {
						// Reject frames that are not the same size as the first
						// frame:
						if (animWidth != bmWidth || animHeight != bmheight) {
							Log.v("loadFramesZIP",
									String.format(
											"Skipping bitmap due to mismatched width (%d != %d) or height (%d != %d)",
											bmWidth, animWidth, bmheight,
											animHeight));
							continue;
						}
					}

					// Add the frame to the set:
					frameSet.put(index, bm);
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

		private void draw() {
			mHandler.removeCallbacks(mUpdateDisplay);

			if (frames == null) {
				mVisible = false;
				return;
			}

			final int frameCount = frames.length;
			SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;

			try {
				c = holder.lockCanvas(null);
				if (c != null) {
					synchronized (holder) {
						final int mCenterX = c.getWidth() / 2;
						final int mCenterY = c.getHeight() / 2;

						// Increment frame counter:
						if (++frame >= frameCount)
							frame = 0;

						Paint paint = new Paint();
						paint.setColor(Color.WHITE);
						paint.setStyle(Paint.Style.STROKE);
						paint.setStrokeJoin(Paint.Join.ROUND);
						paint.setStrokeWidth(4f);

						// Draw the animation frame in the center:
						c.drawBitmap(frames[frame],
								mCenterX - (animWidth / 2f), mCenterY
										- (animHeight / 2f), paint);
					}
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}

			if (mVisible) {
				// 30 fps = 33.3333ms per frame
				mHandler.postDelayed(mUpdateDisplay, 33);
			}
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z,
				Bundle extras, boolean resultRequested) {
			if (action == android.app.WallpaperManager.COMMAND_TAP) {
				return null;
			}
			// TODO Auto-generated method stub
			return super.onCommand(action, x, y, z, extras, resultRequested);
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			mVisible = visible;
			if (visible) {
				if (frames == null)
					tryLoadFrames();

				draw();
			} else {
				mHandler.removeCallbacks(mUpdateDisplay);
			}
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
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
	}
}
