package com.example.kitago

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide

object ImageUtils {
    fun loadProfileImage(context: Context, data: String?, imageView: ImageView) {
        if (data.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.logo_kitago_main)
            return
        }
        if (data.startsWith("http")) {
            Glide.with(context).load(data).circleCrop().placeholder(R.drawable.logo_kitago_main).into(imageView)
        } else {
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                Glide.with(context).load(bitmap).circleCrop().placeholder(R.drawable.logo_kitago_main).into(imageView)
            } catch (_: Exception) {
                imageView.setImageResource(R.drawable.logo_kitago_main)
            }
        }
    }
}

