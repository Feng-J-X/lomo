package com.lomo.app.feature.trash

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lomo.domain.model.Memo
import com.lomo.ui.component.card.MemoCard
import kotlinx.collections.immutable.toImmutableList
import com.lomo.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBackClick: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val pagedTrash = viewModel.pagedTrash.collectAsLazyPagingItems()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    var selectedMemo by remember { mutableStateOf<Memo?>(null) }
    val haptic = com.lomo.ui.util.LocalAppHapticFeedback.current

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                                LargeTopAppBar(
                                        title = { Text(androidx.compose.ui.res.stringResource(R.string.trash_title)) },
                                        navigationIcon = {
                                                IconButton(
                                                        onClick = {
                                                                haptic.medium()
                                                                onBackClick()
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                                contentDescription = androidx.compose.ui.res.stringResource(R.string.back)
                                                        )
                                                }
                                        },
                                        scrollBehavior = scrollBehavior,
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                        )
                                )
                        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val refreshState = pagedTrash.loadState.refresh

            when {
                refreshState is LoadState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                refreshState is LoadState.Error -> {
                    Text(
                            text = refreshState.error.message ?: androidx.compose.ui.res.stringResource(R.string.error_unknown),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                    )
                }
                pagedTrash.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                    com.lomo.ui.component.common.EmptyState(
                            icon = Icons.Rounded.DeleteSweep,
                            title = androidx.compose.ui.res.stringResource(R.string.trash_empty_title),
                            description = androidx.compose.ui.res.stringResource(R.string.trash_empty_desc)
                    )
                }
                else -> {
                    LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                    ) {
                        items(count = pagedTrash.itemCount, key = pagedTrash.itemKey { it.id }) {
                                index ->
                            val memo = pagedTrash[index]
                            if (memo != null) {
                                Box(modifier = Modifier.animateItem()) {
                                    MemoCard(
                                            content = memo.content,
                                            processedContent = memo.content,
                                            timestamp = memo.timestamp,
                                            dateFormat = dateFormat,
                                            timeFormat = timeFormat,
                                            tags = memo.tags.toImmutableList(),
                                            onMenuClick = {
                                                haptic.medium()
                                                selectedMemo = memo
                                            },
                                            menuContent = {
                                                val expanded = selectedMemo == memo
                                                DropdownMenu(
                                                        expanded = expanded,
                                                        onDismissRequest = { selectedMemo = null },
                                                        modifier =
                                                                Modifier.background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceContainer
                                                                ),
                                                        shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                            text = { Text(androidx.compose.ui.res.stringResource(R.string.action_restore)) },
                                                            leadingIcon = {
                                                                Icon(
                                                                        Icons.AutoMirrored.Filled
                                                                                .ArrowBack,
                                                                        contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_restore_memo)
                                                                )
                                                            },
                                                            onClick = {
                                                                haptic.medium()
                                                                viewModel.restoreMemo(memo)
                                                                selectedMemo = null
                                                            }
                                                    )
                                                    DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                        androidx.compose.ui.res.stringResource(R.string.action_delete_permanently),
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                        Icons.Default.DeleteForever,
                                                                        contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_delete_permanently),
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                            },
                                                            onClick = {
                                                                haptic.heavy()
                                                                viewModel.deletePermanently(memo)
                                                                selectedMemo = null
                                                            }
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
