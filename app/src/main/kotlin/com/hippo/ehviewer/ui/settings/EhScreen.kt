package com.hippo.ehviewer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.ehviewer.core.i18n.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.destinations.FilterScreenDestination
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import moe.tarsin.navigate

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.EhScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_eh)) },
                navigationIcon = { NavigationIcon() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val listMode = Settings.listMode.asMutableState()
        Column(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
        ) {
            SimpleMenuPreferenceInt(
                title = stringResource(id = R.string.dark_theme),
                entry = com.hippo.ehviewer.R.array.night_mode_entries,
                entryValueRes = com.hippo.ehviewer.R.array.night_mode_values,
                state = Settings.theme.asMutableState(),
            )
            SwitchPreference(
                title = stringResource(id = R.string.black_dark_theme),
                state = Settings.blackDarkTheme.asMutableState(),
            )
            SwitchPreference(
                title = stringResource(id = R.string.harmonize_category_color),
                state = Settings.harmonizeCategoryColor.asMutableState(),
            )
            SimpleMenuPreferenceInt(
                title = stringResource(id = R.string.settings_eh_list_mode),
                entry = com.hippo.ehviewer.R.array.list_mode_entries,
                entryValueRes = com.hippo.ehviewer.R.array.list_mode_entry_values,
                state = listMode,
            )
            AnimatedVisibility(visible = listMode.value == 0) {
                Column {
                    IntSliderPreference(
                        maxValue = 60,
                        minValue = 20,
                        step = 7,
                        title = stringResource(id = R.string.list_tile_thumb_size),
                        state = Settings.listThumbSize.asMutableState(),
                    )
                    SimpleMenuPreferenceInt(
                        title = stringResource(id = R.string.settings_eh_detail_size),
                        entry = com.hippo.ehviewer.R.array.detail_size_entries,
                        entryValueRes = com.hippo.ehviewer.R.array.detail_size_entry_values,
                        state = Settings.detailSize.asMutableState(),
                    )
                }
            }
            IntSliderPreference(
                maxValue = 10,
                minValue = 1,
                title = stringResource(id = R.string.settings_eh_thumb_columns),
                state = Settings.thumbColumns.asMutableState(),
            )
            SwitchPreference(
                title = stringResource(id = R.string.settings_eh_show_gallery_pages),
                summary = stringResource(id = R.string.settings_eh_show_gallery_pages_summary),
                state = Settings.showGalleryPages.asMutableState(),
            )
            SwitchPreference(
                title = stringResource(id = R.string.settings_eh_show_reading_progress),
                summary = stringResource(id = R.string.settings_eh_show_reading_progress_summary),
                state = Settings.showReadingProgress.asMutableState(),
            )
            if (EhTagDatabase.translatable) {
                SwitchPreference(
                    title = stringResource(id = R.string.settings_eh_show_tag_translations),
                    summary = stringResource(id = R.string.settings_eh_show_tag_translations_summary),
                    state = Settings.showTagTranslations.asMutableState(),
                )
                UrlPreference(
                    title = stringResource(id = R.string.settings_eh_tag_translations_source),
                    url = stringResource(id = R.string.settings_eh_tag_translations_source_url),
                )
            }
            Preference(
                title = stringResource(id = R.string.settings_eh_filter),
                summary = stringResource(id = R.string.settings_eh_filter_summary),
            ) { navigate(FilterScreenDestination) }
        }
    }
}
