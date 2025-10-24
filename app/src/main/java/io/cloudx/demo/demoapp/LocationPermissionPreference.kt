package io.cloudx.demo.demoapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class LocationPermissionPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {

    private val ctx = context as Activity

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnClickListener {
            val reqCode = 5

            ActivityCompat.requestPermissions(
                ctx,
                arrayOf(
                    ACCESS_COARSE_LOCATION,
                    ACCESS_FINE_LOCATION
                ),
                reqCode
            )
        }
    }
}