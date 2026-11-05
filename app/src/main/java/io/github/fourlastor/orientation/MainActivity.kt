package io.github.fourlastor.orientation

import android.app.StatusBarManager
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OrientationTheme {
                OrientationScreen(onGrant = { startActivity(Rotation.permissionIntent(this)) })
            }
        }
    }
}

@Composable
private fun OrientationTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private class TileEntry(
    val service: Class<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
)

private val tiles = listOf(
    TileEntry(PortraitTile::class.java, R.string.tile_portrait, R.drawable.ic_portrait),
    TileEntry(ReversePortraitTile::class.java, R.string.tile_reverse, R.drawable.ic_portrait_reverse),
    TileEntry(LandscapeTile::class.java, R.string.tile_landscape, R.drawable.ic_landscape),
    TileEntry(AutoRotateTile::class.java, R.string.tile_auto, R.drawable.ic_rotation),
)

@Composable
private fun OrientationScreen(onGrant: () -> Unit) {
    val context = LocalContext.current
    // Bumped whenever something outside Compose may have changed: the settings or the permission.
    var version by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        version++
        onPauseOrDispose {}
    }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        listOf(Settings.System.ACCELEROMETER_ROTATION, Settings.System.USER_ROTATION).forEach {
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, observer)
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val canWrite = remember(version) { Rotation.canWrite(context) }
    val auto = remember(version) { Rotation.isAuto(context) }
    val locked = remember(version) {
        if (auto) null else Rotation.Orientation.of(context, Rotation.current(context))
    }

    // No app bar: the title sits in the content column so it lines up with it on wide screens.
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (!canWrite) PermissionCard(onGrant)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.tile_auto)) },
                        supportingContent = { Text(stringResource(R.string.auto_rotate_summary)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_rotation), contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = auto,
                                onCheckedChange = { Rotation.setAuto(context, it) },
                                enabled = canWrite,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }

                SectionHeader(R.string.lock_header)
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    tiles.take(3).zip(Rotation.Orientation.entries.sortedBy { it.uiOrder }).forEach { (tile, orientation) ->
                        OrientationOption(
                            label = tile.label,
                            icon = tile.icon,
                            selected = canWrite && locked == orientation,
                            enabled = canWrite,
                            onClick = { Rotation.lock(context, orientation) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (auto) {
                    Text(
                        stringResource(R.string.lock_hint_auto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (Build.VERSION.SDK_INT >= 33) {
                    SectionHeader(R.string.tiles_header)
                    Text(
                        stringResource(R.string.tiles_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.forEach { tile ->
                            AssistChip(
                                onClick = { requestAddTile(context, tile) },
                                label = { Text(stringResource(tile.label)) },
                                leadingIcon = {
                                    Icon(painterResource(tile.icon), contentDescription = null, Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The order the options appear in: Portrait, Reverse portrait, Landscape — same as [tiles]. */
private val Rotation.Orientation.uiOrder
    get() = when (this) {
        Rotation.Orientation.PORTRAIT -> 0
        Rotation.Orientation.REVERSE_PORTRAIT -> 1
        Rotation.Orientation.LANDSCAPE -> 2
    }

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onGrant) { Text(stringResource(R.string.permission_action)) }
        }
    }
}

@Composable
private fun OrientationOption(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // A state change met a few times a day: fast effects tier, colour only. With animations off it
    // snaps, and the selection still reads from the container colour at rest.
    val spec = MaterialTheme.motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()
    val container by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainer, spec)
    val content by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, spec)
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = if (enabled) content else content.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun requestAddTile(context: android.content.Context, tile: TileEntry) {
    if (Build.VERSION.SDK_INT < 33) return
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, tile.service),
        context.getString(tile.label),
        Icon.createWithResource(context, tile.icon),
        context.mainExecutor,
    ) {}
}
