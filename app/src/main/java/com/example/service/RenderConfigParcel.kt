package com.example.service

import android.os.Parcel
import android.os.Parcelable

data class RenderConfigParcel(
    val imageIndex: Int,
    val movementId: Int,
    val durationSeconds: Float
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(imageIndex)
        parcel.writeInt(movementId)
        parcel.writeFloat(durationSeconds)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RenderConfigParcel> {
        override fun createFromParcel(parcel: Parcel): RenderConfigParcel = RenderConfigParcel(parcel)
        override fun newArray(size: Int): Array<RenderConfigParcel?> = arrayOfNulls(size)
    }
}
