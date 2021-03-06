Wallpaper Animator
==================

This project is a simple Android live wallpaper. It loops an animation of numbered image frames found in a ZIP file.

Try it!
=======
Downloads available at: http://bittwiddlers.org/ftp/jim/software/android/wallpaper-animator/

1. Download `android-wallpaper-animator-master.apk` (50KB) to your android device and install it
2. Download the sample `wp-fry.zip` (20MB) to your android device
3. Extract `wp-fry.zip` to the root of your primary SD card, e.g. `/storage/emulated/0/` (depends on device)
4. Extraction should preserve folders and should create a `/animated-wallpaper/` folder
5. Set your live wallpaper to "Wallpaper Animator" (long-press home screen in an empty space)

Notes
=====
It's rather unforgiving at the moment. If you don't put the files in the right place it'll just show a black screen.

The wallpaper is currently hard-coded to run at ~30fps to be compatible with most popular frame rates.

You'll need a decent CPU speed because bitmaps are decoded from the SD card each frame. There is simply not enough
memory allowed to be used for bitmaps; this limitation is imposed by the OS and the limit varies by ROM.

Caveats
=======
* The folder must be named `animated-wallpaper` and it must to be found in the primary SD card root folder (what `Environment.getExternalStorageDirectory()` returns).
* The folder can contain any image files you like but must only contain image files (i.e. no .txt files or otherwise)
* All images must share the same dimensions (720x1080 recommended for portait mode for modern Android devices)
* Due to resource constraints, multiple layers of animation are not supported
* Each filename must be named as `(anything here)###.extension`. File extension can be any Android OS supported image format.
* Frame numbers are just digit sequences and don't have to be sequential
* Frame numbers are only used for relative ordering; they are *not* used as absolute keyframe identifiers
