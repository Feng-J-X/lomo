package com.lomo.app.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Language
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lomo.ui.component.dialog.SelectionDialog
import com.lomo.ui.component.settings.PreferenceItem
import com.lomo.ui.theme.MotionTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
        private val repository: com.lomo.domain.repository.MemoRepository,
        private val formatDetector: com.lomo.data.util.FormatDetector,
        private val fileDataSource: com.lomo.data.source.FileDataSource
) : ViewModel() {

        val rootDirectory: StateFlow<String> =
                repository
                        .getRootDisplayName()
                        .map { it ?: "" }
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

        val imageDirectory: StateFlow<String> =
                repository
                        .getImageDisplayName()
                        .map { it ?: "" }
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

        val dateFormat: StateFlow<String> =
                repository
                        .getDateFormat()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.DATE_FORMAT
                        )

        val timeFormat: StateFlow<String> =
                repository
                        .getTimeFormat()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.TIME_FORMAT
                        )

        val themeMode: StateFlow<String> =
                repository
                        .getThemeMode()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.THEME_MODE
                        )

        val hapticFeedbackEnabled: StateFlow<Boolean> =
                repository
                        .isHapticFeedbackEnabled()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.HAPTIC_FEEDBACK_ENABLED
                        )

        val storageFilenameFormat: StateFlow<String> =
                repository
                        .getStorageFilenameFormat()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.STORAGE_FILENAME_FORMAT
                        )

        val storageTimestampFormat: StateFlow<String> =
                repository
                        .getStorageTimestampFormat()
                        .stateIn(
                                viewModelScope,
                                SharingStarted.WhileSubscribed(5000),
                                com.lomo.data.util.PreferenceKeys.Defaults.STORAGE_TIMESTAMP_FORMAT
                        )

        fun updateRootDirectory(path: String) {
                viewModelScope.launch { repository.setRootDirectory(path) }
        }

        fun updateRootUri(uriString: String) {
                viewModelScope.launch { repository.updateRootUri(uriString) }
        }

        fun updateImageDirectory(path: String) {
                viewModelScope.launch { repository.setImageDirectory(path) }
        }

        fun updateImageUri(uriString: String) {
                viewModelScope.launch { repository.updateImageUri(uriString) }
        }

        fun updateDateFormat(format: String) {
                viewModelScope.launch { repository.setDateFormat(format) }
        }

        fun updateTimeFormat(format: String) {
                viewModelScope.launch { repository.setTimeFormat(format) }
        }

        fun updateThemeMode(mode: String) {
                viewModelScope.launch { repository.setThemeMode(mode) }
        }

        fun updateStorageFilenameFormat(format: String) {
                viewModelScope.launch { repository.setStorageFilenameFormat(format) }
        }

        fun updateStorageTimestampFormat(format: String) {
                viewModelScope.launch { repository.setStorageTimestampFormat(format) }
        }

        fun autoDetectFormats() {
                viewModelScope.launch {
                        // We need to access files to detect.
                        // Repository abstracts PagingData but we need raw list.
                        // We can use FileDataSource directly if exposed or added to Repo.
                        // Added FileDataSource to constructor for this utility access.

                        try {
                                val files = fileDataSource.listFiles()
                                if (files.isEmpty()) return@launch

                                val filenames = files.map { it.filename }
                                val contents = files.map { it.content.lines().firstOrNull() ?: "" }

                                val (detectedFilename, detectedTimestamp) =
                                        formatDetector.detectFormats(filenames, contents)

                                if (detectedFilename != null) {
                                        repository.setStorageFilenameFormat(detectedFilename)
                                }
                                if (detectedTimestamp != null) {
                                        repository.setStorageTimestampFormat(detectedTimestamp)
                                }
                        } catch (e: Exception) {
                                // Log error
                                android.util.Log.e("SettingsViewModel", "Auto-detect failed", e)
                        }
                }
        }

        fun updateHapticFeedback(enabled: Boolean) {
                viewModelScope.launch { repository.setHapticFeedbackEnabled(enabled) }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
        val rootDir by viewModel.rootDirectory.collectAsStateWithLifecycle()
        val imageDir by viewModel.imageDirectory.collectAsStateWithLifecycle()
        val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
        val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val context = LocalContext.current

        var showDateDialog by remember { mutableStateOf(false) }
        var showTimeDialog by remember { mutableStateOf(false) }
        var showThemeDialog by remember { mutableStateOf(false) }
        var showFilenameDialog by remember { mutableStateOf(false) }
        var showTimestampDialog by remember { mutableStateOf(false) }
        var showLanguageDialog by remember { mutableStateOf(false) }

        val dateFormats = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd")
        val timeFormats = listOf("HH:mm", "hh:mm a", "HH:mm:ss", "hh:mm:ss a")
        val themeModes = listOf("system", "light", "dark")
        val themeModeLabels = mapOf(
            "system" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_system),
            "light" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_light_mode),
            "dark" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_dark_mode)
        )
        val languageLabels = mapOf(
            "system" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_system),
            "en" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_english),
            "zh-CN" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_simplified_chinese),
            "zh-Hans-CN" to androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_simplified_chinese)
        )
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLanguageTag = if (!currentLocales.isEmpty) currentLocales[0]?.toLanguageTag() ?: "system" else "system"

        // Launcher for root directory
        val rootLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                        uri?.let {
                                val flags =
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                context.contentResolver.takePersistableUriPermission(it, flags)
                                viewModel.updateRootUri(it.toString())
                                // Do not update root directory path here, rely on URI.
                        }
                }

        // Launcher for image directory
        val imageLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                        uri?.let {
                                val flags =
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                context.contentResolver.takePersistableUriPermission(it, flags)
                                viewModel.updateImageUri(it.toString())
                                // Do not update image directory path here, rely on URI.
                        }
                }

        val haptic = com.lomo.ui.util.LocalAppHapticFeedback.current

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        
        AnimatedContent(
            targetState = currentLanguageTag,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = MotionTokens.DurationLong2, easing = MotionTokens.EasingEmphasized)) + 
                 scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = MotionTokens.DurationLong2, easing = MotionTokens.EasingEmphasized)))
                    .togetherWith(fadeOut(animationSpec = tween(durationMillis = MotionTokens.DurationLong2, easing = MotionTokens.EasingEmphasized)))
            },
            label = "LanguageTransition"
        ) { tag ->
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_title)) },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    haptic.medium()
                                    onBackClick()
                                }
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.lomo.app.R.string.back)) }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            ) { padding ->
                Column(
                    modifier =
                        Modifier.padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsGroup(title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_group_storage)) {
                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_memo_directory),
                            subtitle = if (rootDir.isNotEmpty()) rootDir else androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_not_set),
                            icon = Icons.Default.Folder,
                            onClick = { rootLauncher.launch(null) }
                        )

                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_image_storage),
                            subtitle =
                                if (imageDir.isNotEmpty()) imageDir
                                else androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_not_set),
                            icon = Icons.Outlined.PhotoLibrary,
                            onClick = { imageLauncher.launch(null) }
                        )

                        val filenameFormatVal by
                            viewModel.storageFilenameFormat
                                .collectAsStateWithLifecycle()
                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_filename_format),
                            subtitle = filenameFormatVal,
                            icon = Icons.Outlined.Description,
                            onClick = { showFilenameDialog = true }
                        )

                        val timestampFormatVal by
                            viewModel.storageTimestampFormat
                                .collectAsStateWithLifecycle()
                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_timestamp_format),
                            subtitle = timestampFormatVal,
                            icon = Icons.Outlined.AccessTime,
                            onClick = { showTimestampDialog = true }
                        )

                        FilledTonalButton(
                            onClick = {
                                haptic.medium()
                                viewModel.autoDetectFormats()
                            },
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_auto_detect)) }
                    }

                    SettingsGroup(title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_group_display)) {
                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_language),
                            subtitle = languageLabels[tag] ?: tag,
                            icon = Icons.Outlined.Language,
                            onClick = { showLanguageDialog = true }
                        )

                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_theme_mode),
                            subtitle = themeModeLabels[themeMode] ?: themeMode,
                            icon = Icons.Outlined.Brightness6,
                            onClick = { showThemeDialog = true }
                        )

                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_date_format),
                            subtitle = dateFormat,
                            icon = Icons.Outlined.CalendarToday,
                            onClick = { showDateDialog = true }
                        )

                        PreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_time_format),
                            subtitle = timeFormat,
                            icon = Icons.Outlined.Schedule,
                            onClick = { showTimeDialog = true }
                        )
                    }

                    SettingsGroup(title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_group_interaction)) {
                        val hapticEnabledVal by
                            viewModel.hapticFeedbackEnabled
                                .collectAsStateWithLifecycle()
                        SwitchPreferenceItem(
                            title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_haptic_feedback),
                            subtitle = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_haptic_feedback_subtitle),
                            icon = Icons.Default.Vibration, 
                            checked = hapticEnabledVal,
                            onCheckedChange = { viewModel.updateHapticFeedback(it) }
                        )
                    }
                }
            }
        }

        if (showDateDialog) {
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_date_format),
                options = dateFormats,
                currentSelection = dateFormat,
                onDismiss = { showDateDialog = false },
                onSelect = {
                    viewModel.updateDateFormat(it)
                    showDateDialog = false
                }
            )
        }

        if (showTimeDialog) {
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_time_format),
                options = timeFormats,
                currentSelection = timeFormat,
                onDismiss = { showTimeDialog = false },
                onSelect = {
                    viewModel.updateTimeFormat(it)
                    showTimeDialog = false
                }
            )
        }

        if (showThemeDialog) {
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_theme),
                options = themeModes,
                currentSelection = themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = {
                    viewModel.updateThemeMode(it)
                    showThemeDialog = false
                },
                labelProvider = { themeModeLabels[it] ?: it }
            )
        }

        if (showLanguageDialog) {
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_language),
                options = listOf("system", "zh-CN", "en"),
                currentSelection = currentLanguageTag,
                onDismiss = { showLanguageDialog = false },
                onSelect = { tag ->
                    val locales = if (tag == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                    showLanguageDialog = false
                },
                labelProvider = { languageLabels[it] ?: it }
            )
        }

        if (showFilenameDialog) {
            val fFormat by viewModel.storageFilenameFormat.collectAsStateWithLifecycle()
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_filename_format),
                options =
                    listOf(
                        "yyyy_MM_dd",
                        "yyyy-MM-dd",
                        "yyyy.MM.dd",
                        "yyyyMMdd",
                        "MM-dd-yyyy"
                    ),
                currentSelection = fFormat,
                onDismiss = { showFilenameDialog = false },
                onSelect = {
                    viewModel.updateStorageFilenameFormat(it)
                    showFilenameDialog = false
                }
            )
        }

        if (showTimestampDialog) {
            val tFormat by viewModel.storageTimestampFormat.collectAsStateWithLifecycle()
            SelectionDialog(
                title = androidx.compose.ui.res.stringResource(com.lomo.app.R.string.settings_select_timestamp_format),
                options =
                    listOf(
                        "HH:mm:ss",
                        "HH:mm",
                        "hh:mm a",
                        "yyyy-MM-dd HH:mm:ss"
                    ),
                currentSelection = tFormat,
                onDismiss = { showTimestampDialog = false },
                onSelect = {
                    viewModel.updateStorageTimestampFormat(it)
                    showTimestampDialog = false
                }
            )
        }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column {
                Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 16.dp) // Aligned with list items
                )
                Column(modifier = Modifier.fillMaxWidth()) { content() }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
}

@Composable
fun SwitchPreferenceItem(
        title: String,
        subtitle: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        val haptic = com.lomo.ui.util.LocalAppHapticFeedback.current
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable {
                                        haptic.medium()
                                        onCheckedChange(!checked)
                                }
                                .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
}
