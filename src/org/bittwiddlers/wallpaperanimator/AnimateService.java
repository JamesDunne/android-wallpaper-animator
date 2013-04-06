package org.bittwiddlers.wallpaperanimator;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

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
	    private int frameCount = 60;

	    public AnimateEngine() {
	    }

        private void draw() {
            mHandler.removeCallbacks(mUpdateDisplay);

            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;

            try {
                c = holder.lockCanvas(null);
                if (c != null) {
                	synchronized (holder) {
	                	// Increment frame counter:
	                	if (++frame >= frameCount) frame = 0;
	
	                	final int mCenterX = c.getWidth() / 2;
	                	final int mCenterY = c.getHeight() / 2;
	
	                	Paint paint = new Paint();
	                	paint.setColor(Color.WHITE);
	        	        paint.setStyle(Paint.Style.STROKE);
	        	        paint.setStrokeJoin(Paint.Join.ROUND);
	        	        paint.setStrokeWidth(4f);
	        	        
	        	        // Clear screen:
	        	        c.drawColor(Color.BLACK);
	
	        	        // Draw my circle:
	        	        c.drawCircle(
	                		(float)mCenterX + (float)Math.sin((double)frame * Math.PI * 2.0 / (double)frameCount) * ((float)c.getWidth() / 2.0f),
	                		mCenterY,
	                		50f,
	                		paint
	                	);
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
                draw();
            } else {
                mHandler.removeCallbacks(mUpdateDisplay);
            }
        }
 
        @Override
        public void onSurfaceChanged(SurfaceHolder holder,
                    int format, int width, int height) {
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
