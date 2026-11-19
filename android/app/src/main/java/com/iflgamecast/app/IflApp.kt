package com.iflgamecast.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder

class IflApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Team logos are PNG or SVG files served by the Gamecast server
        Coil.setImageLoader(ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }.crossfade(true).build())
    }
}
