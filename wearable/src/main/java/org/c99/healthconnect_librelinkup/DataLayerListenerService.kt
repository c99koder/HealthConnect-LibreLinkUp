/*
 * Copyright (c) 2024 Sam Steele
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.c99.healthconnect_librelinkup

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import org.c99.healthconnect_librelinkup.complication.GlucoseComplicationService
import org.c99.healthconnect_librelinkup.tile.GlucoseTileService

class DataLayerListenerService : WearableListenerService() {
    companion object {
        const val GLUCOSE_KEY: String = "org.c99.healthconnect_librelinkup.glucose"
        const val TREND_ARROW_KEY: String = "org.c99.healthconnect_librelinkup.trendArrow"
        const val COLOR_KEY: String = "org.c99.healthconnect_librelinkup.color"
        const val UNITS_KEY = "org.c99.healthconnect_librelinkup.units"
        const val TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val uri = event.dataItem.uri

            if (uri.path == "/glucose") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                val glucose =
                    applicationContext.getSharedPreferences("glucose", MODE_PRIVATE).edit()
                glucose.putFloat(GLUCOSE_KEY, dataMap.getFloat(GLUCOSE_KEY))
                glucose.putInt(TREND_ARROW_KEY, dataMap.getInt(TREND_ARROW_KEY))
                glucose.putInt(COLOR_KEY, dataMap.getInt(COLOR_KEY))
                glucose.putInt(UNITS_KEY, dataMap.getInt(UNITS_KEY))
                glucose.putString(TIMESTAMP_KEY, dataMap.getString(TIMESTAMP_KEY))
                glucose.commit()

                ComplicationDataSourceUpdateRequester.create(
                    applicationContext,
                    ComponentName(applicationContext, GlucoseComplicationService::class.java)
                ).requestUpdateAll()
                TileService.getUpdater(applicationContext).requestUpdate(GlucoseTileService::class.java)
            }
        }
    }
}