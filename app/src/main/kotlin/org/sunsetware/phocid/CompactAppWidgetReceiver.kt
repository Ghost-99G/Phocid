// app/src/main/kotlin/org/sunsetware/phocid/CompactAppWidgetReceiver.kt
package org.sunsetware.phocid

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.utils.combine

class CompactAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CompactAppWidget()
}

class CompactAppWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        while (!GlobalData.initialized.get()) {
            delay(1)
        }
        val coroutineScope = CoroutineScope(coroutineContext + Dispatchers.IO)
        val trackState =
            GlobalData.libraryIndex.combine(
                coroutineScope,
                GlobalData.playerState,
            ) { libraryIndex, playerState ->
                libraryIndex.tracks[
                        playerState.actualPlayQueue.getOrNull(playerState.currentIndex),
                    ]
            }

        provideContent {
            val resources = LocalContext.current.resources
            val track by trackState.collectAsState()
            val playerTransientState by GlobalData.playerTransientState.collectAsState()

            val backgroundRadius =
                resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    resources.displayMetrics.density

            GlanceTheme {
                Box(
                    modifier =
                        GlanceModifier.cornerRadius(backgroundRadius.dp)
                            .background(ColorProvider.systemColor(ColorProvider.Type.Surface))
                            .fillMaxSize()
                            .clickable(actionStartActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    WidgetContent(track, playerTransientState.isPlaying)
                }
            }
        }
    }
}

@Composable
private fun WidgetContent(track: Track?, isPlaying: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.padding(horizontal = 12.dp).fillMaxSize(),
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = track?.title ?: Strings[R.string.commons_unknown]
            val artist = track?.artist ?: Strings[R.string.commons_unknown]

            Text(
                title,
                maxLines = 1,
                style = TextStyle(ColorProvider.systemColor(ColorProvider.Type.OnSurface)),
            )
            Text(
                artist,
                maxLines = 1,
                style = TextStyle(ColorProvider.systemColor(ColorProvider.Type.OnSurfaceVariant)),
            )
        }
        Controls(isPlaying)
    }
}

@Composable
private fun Controls(isPlaying: Boolean) {
    @Composable
    fun IconButton(
        @DrawableRes icon: Int,
        description: String,
        modifier: GlanceModifier,
    ) {
        Image(
            ImageProvider(icon),
            description,
            modifier = modifier.size(48.dp),
            colorFilter = ColorFilter.tint(ColorProvider.systemColor(ColorProvider.Type.OnSurface)),
        )
    }

    Row(
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            R.drawable.player_previous,
            Strings[R.string.player_previous],
            GlanceModifier.clickable { withController { it.seekToPrevious() } },
        )
        Spacer(GlanceModifier.size(8.dp))
        IconButton(
            if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
            if (isPlaying) Strings[R.string.player_pause] else Strings[R.string.player_play],
            GlanceModifier.clickable { withController { if (it.isPlaying) it.pause() else it.play() } },
        )
        Spacer(GlanceModifier.size(8.dp))
        IconButton(
            R.drawable.player_next,
            Strings[R.string.player_next],
            GlanceModifier.clickable { withController { it.seekToNext() } },
        )
    }
}

private inline fun withController(crossinline action: (MediaController) -> Unit) {
    val context = LocalContext.current
    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
    controllerFuture.addListener(
        { action(controllerFuture.get()) },
        MoreExecutors.directExecutor(),
    )
}
