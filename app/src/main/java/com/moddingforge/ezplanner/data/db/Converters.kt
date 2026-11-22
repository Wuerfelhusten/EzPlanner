package com.moddingforge.ezplanner.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
