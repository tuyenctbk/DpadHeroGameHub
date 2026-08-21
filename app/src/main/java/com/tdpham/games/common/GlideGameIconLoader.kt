package com.tdpham.games.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * High-performance Glide-based icon loader that downsamples and caches
 * game card icons in memory to optimize TV GPU/RAM usage during fast scrolling.
 */
object GlideGameIconLoader {

    private val baseOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .skipMemoryCache(false)

    fun loadButtonTopIcon(
        context: Context,
        button: Button,
        @DrawableRes iconResId: Int,
        targetWidthDp: Int = 96,
        targetHeightDp: Int = 96
    ) {
        val density = context.resources.displayMetrics.density
        val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(1)
        val targetHeightPx = (targetHeightDp * density).toInt().coerceAtLeast(1)

        Glide.with(button.context)
            .asBitmap()
            .apply(baseOptions)
            .load(iconResId)
            .override(targetWidthPx, targetHeightPx)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    val drawable = BitmapDrawable(button.resources, resource)
                    button.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    button.setCompoundDrawablesWithIntrinsicBounds(null, placeholder, null, null)
                }
            })
    }

    fun loadImageViewIcon(
        context: Context,
        imageView: ImageView,
        @DrawableRes iconResId: Int,
        targetWidthDp: Int = 96,
        targetHeightDp: Int = 96
    ) {
        val density = context.resources.displayMetrics.density
        val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(1)
        val targetHeightPx = (targetHeightDp * density).toInt().coerceAtLeast(1)

        Glide.with(imageView.context)
            .load(iconResId)
            .apply(baseOptions)
            .override(targetWidthPx, targetHeightPx)
            .into(imageView)
    }

    fun preloadIcons(
        context: Context,
        @DrawableRes iconResIds: Collection<Int>,
        targetWidthDp: Int = 96,
        targetHeightDp: Int = 96
    ) {
        val density = context.resources.displayMetrics.density
        val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(1)
        val targetHeightPx = (targetHeightDp * density).toInt().coerceAtLeast(1)

        val glide = Glide.with(context.applicationContext)
        for (resId in iconResIds) {
            glide.asBitmap()
                .apply(baseOptions)
                .load(resId)
                .override(targetWidthPx, targetHeightPx)
                .preload()
        }
    }
}

