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

package org.c99.healthconnect_librelinkup.tile

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.LayoutRootPreview
import com.google.android.horologist.compose.tools.buildDeviceParameters
import com.google.android.horologist.tiles.SuspendingTileService
import org.c99.healthconnect_librelinkup.DataLayerListenerService
import org.c99.healthconnect_librelinkup.R
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val RESOURCES_VERSION = "1"

@OptIn(ExperimentalHorologistApi::class)
class GlucoseTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
            .addIdToImageMapping("arrow_down", ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(R.drawable.arrow_down)
                    .build()
                ).build()
            )
            .addIdToImageMapping("arrow_down_right", ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.arrow_down_right)
                        .build()
                ).build()
            )
            .addIdToImageMapping("arrow_right", ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.arrow_right)
                        .build()
                ).build()
            )
            .addIdToImageMapping("arrow_up_right", ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.arrow_up_right)
                        .build()
                ).build()
            )
            .addIdToImageMapping("arrow_up", ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.arrow_up)
                        .build()
                ).build()
            )
            .build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {

        val glucose = getSharedPreferences("glucose", MODE_PRIVATE)
        if (glucose != null && glucose.contains(DataLayerListenerService.GLUCOSE_KEY)) {
            val icon = when (glucose.getInt(DataLayerListenerService.TREND_ARROW_KEY, -1)) {
                1 -> "arrow_down"
                2 -> "arrow_down_right"
                3 -> "arrow_right"
                4 -> "arrow_up_right"
                5 -> "arrow_up"
                else -> ""
            }

            val color = when (glucose.getInt(DataLayerListenerService.COLOR_KEY, -1)) {
                1 -> resources.getColor(R.color.normal, theme)
                2 -> resources.getColor(R.color.high, theme)
                3 -> resources.getColor(R.color.very_high, theme)
                4 -> resources.getColor(R.color.low, theme)
                else -> Colors.DEFAULT.primary
            }

            val time = ZonedDateTime.parse(
                glucose.getString(DataLayerListenerService.TIMESTAMP_KEY, "") + " +0000",
                DateTimeFormatter.ofPattern("M/d/y h:m:s a Z")
            )

            val singleTileTimeline = TimelineBuilders.Timeline.Builder().addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder().setLayout(
                    LayoutElementBuilders.Layout.Builder().setRoot(
                        tileLayout(this,
                            glucose.getFloat(DataLayerListenerService.GLUCOSE_KEY, 0f),
                            icon,
                            color,
                            DateUtils.getRelativeTimeSpanString(time.toEpochSecond()*1000L).toString(),
                            glucose.getInt(DataLayerListenerService.UNITS_KEY, 1))).build()
                ).build()
            ).build()

            return TileBuilders.Tile.Builder().setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(singleTileTimeline).build()
        }

        val singleTileTimeline = TimelineBuilders.Timeline.Builder().addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder().setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(noDataLayout(this)).build()
            ).build()
        ).build()

        return TileBuilders.Tile.Builder().setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60 * 1000)
            .setTileTimeline(singleTileTimeline).build()
    }
}

@SuppressLint("DefaultLocale")
private fun tileLayout(context: Context, glucose: Float, arrow: String, color: Int, secondaryLabel: String, units: Int): LayoutElementBuilders.LayoutElement {
    return PrimaryLayout.Builder(buildDeviceParameters(context.resources))
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(Text.Builder(context, "Blood Glucose")
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(Colors.DEFAULT.primary))
            .build())
        .setSecondaryLabelTextContent(Text.Builder(context, secondaryLabel)
            .setTypography(Typography.TYPOGRAPHY_CAPTION3)
            .setColor(argb(Colors.DEFAULT.onSurface))
            .build())
        .setContent(
            Chip.Builder(context, ModifiersBuilders.Clickable.Builder().build(), buildDeviceParameters(context.resources))
                .setChipColors(ChipColors(argb(color), argb(context.getColor(R.color.glucose_text))))
                .setCustomContent(
                    LayoutElementBuilders.Row.Builder()
                        .addContent(
                            LayoutElementBuilders.Image.Builder()
                                .setResourceId(arrow)
                                .setWidth(dp(24f))
                                .setHeight(dp(24f))
                                .setColorFilter(ColorFilter.Builder().setTint(argb(context.getColor(R.color.glucose_text))).build())
                                .build()

                        )
                        .addContent(
                            LayoutElementBuilders.Row.Builder()
                                .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                                .addContent(
                                    Text.Builder(context,
                                        when(units) {
                                            1 -> String.format("%.0f", glucose)
                                            else -> String.format("%.1f", glucose)
                                        })
                                        .setTypography(Typography.TYPOGRAPHY_TITLE1)
                                        .setColor(argb(context.getColor(R.color.glucose_text)))
                                        .build()
                                )
                                .addContent(
                                    Text.Builder(context,
                                        when(units) {
                                            1 -> " mg/dL"
                                            else -> " mmol"
                                        })
                                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                        .setColor(argb(context.getColor(R.color.glucose_text)))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .setWidth(dp(140f))
                .build()
        )
        .build()
}

private fun noDataLayout(context: Context): LayoutElementBuilders.LayoutElement {
    return PrimaryLayout.Builder(buildDeviceParameters(context.resources))
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(Text.Builder(context, "No Data")
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(Colors.DEFAULT.primary))
            .build())
        .setSecondaryLabelTextContent(Text.Builder(context, "Check your username and password in the companion app on your phone")
            .setTypography(Typography.TYPOGRAPHY_BODY2)
            .setColor(argb(Colors.DEFAULT.onSurface))
            .setMaxLines(4)
            .build())
        .build()
}


@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun NoDataPreview() {
    LayoutRootPreview(root = noDataLayout(LocalContext.current))
}
@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun LowPreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current, 60f, "arrow_down", LocalContext.current.getColor(R.color.low), "5 minutes ago", 1))
}
@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun NormalPreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current, 100f, "arrow_right", LocalContext.current.getColor(R.color.normal), "10 minutes ago", 1))
}
@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun HighPreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current, 180f, "arrow_up_right", LocalContext.current.getColor(R.color.high), "15 minutes ago", 1))
}
@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun VeryHighPreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current, 300f, "arrow_up", LocalContext.current.getColor(R.color.very_high), "20 minutes ago", 1))
}
