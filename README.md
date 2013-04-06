Wallpaper Animator
==================

This project is a simple Android live wallpaper. It loops an animation of numbered image frames found in a ZIP file.

Try it!
=======
Downloads available at: http://bittwiddlers.org/ftp/jim/software/android/wallpaper-animator/

1. Download `WallpaperAnimator-master.apk` (200KB) to your android device and install it
2. Download the sample `animated-wallpaper.zip` (20MB) to your android device
3. Move `animated-wallpaper.zip` to the root of your primary SD card, e.g. `/mnt/sdcard/` or `/storage/emulated/0/`
4. Set your live wallpaper to "Wallpaper Animator"
5. Watch it go!

Notes
=====
It's rather unforgiving at the moment. If you don't put the zip file in the right place it'll just show a black screen.

The wallpaper is currently hard-coded to run at ~30fps to be compatible with most popular frame rates.

Caveats
=======
* The zip file must be named `animated-wallpaper.zip` and it must to be found in the primary SD card root folder (what `Environment.getExternalStorageDirectory()` returns).
* The zip file can contain any image files you like but must only contain image files (i.e. no .txt files or otherwise)
* Each image file name must contain a frame number as the last part of the file name before the file extension.
* Frame numbers are just a sequence of digits [0-9].
* Frame numbers don't have to start at one or zero, but they do have to be unique.
* Frame numbers are only used for relative ordering; they are *not* used as absolute keyframe identifier.
* All frame images must be the same dimensions.
