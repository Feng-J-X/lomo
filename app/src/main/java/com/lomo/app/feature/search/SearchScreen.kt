package com.lomo.app.feature.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lomo.domain.model.Memo
import com.lomo.ui.component.card.MemoCard
import com.lomo.ui.component.common.EmptyState
import com.lomo.ui.component.common.SkeletonMemoItem
import kotlinx.collections.immutable.toImmutableList
import com.lomo.ui.util.formatAsDateTime

import com.lomo.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBackClick: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query: String by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pagedResults = viewModel.pagedResults.collectAsLazyPagingItems()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val haptic = com.lomo.ui.util.LocalAppHapticFeedback.current
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Wrap with MemoMenuHost for menu support
    com.lomo.ui.component.menu.MemoMenuHost(
        onEdit = { /* TODO: implement edit */ },
        onDelete = { /* TODO: implement delete */ }
    ) { showMenu ->
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        val interactionSource = remember { MutableInteractionSource() }
                        BasicTextField(
                            value = query,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = interactionSource,
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(R.string.search_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.medium()
                            onBackClick()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = androidx.compose.ui.res.stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                haptic.medium()
                                viewModel.onSearchQueryChanged("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            val refreshState = pagedResults.loadState.refresh
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (query.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = androidx.compose.ui.res.stringResource(R.string.search_empty_initial_title),
                            description = androidx.compose.ui.res.stringResource(R.string.search_empty_initial_desc)
                        )
                    }
                } else {
                    when (refreshState) {
                        is LoadState.Loading -> {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(5) { SkeletonMemoItem() }
                            }
                        }
                        is LoadState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = refreshState.error.message ?: androidx.compose.ui.res.stringResource(R.string.search_error_generic),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {
                            if (pagedResults.itemCount == 0) {
                                EmptyState(
                                    icon = Icons.Default.Search,
                                    title = androidx.compose.ui.res.stringResource(R.string.search_no_results_title),
                                    description = androidx.compose.ui.res.stringResource(R.string.search_no_results_desc)
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        top = 16.dp, 
                                        start = 16.dp, 
                                        end = 16.dp,
                                        bottom = 16.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        count = pagedResults.itemCount,
                                        key = pagedResults.itemKey { it.memo.id },
                                        contentType = { "memo" }
                                    ) { index ->
                                        val uiModel = pagedResults[index]
                                        if (uiModel != null) {
                                            val memo = uiModel.memo
                                            Box(modifier = Modifier.animateItem()) {
                                                    MemoCard(
                                                        content = memo.content,
                                                        processedContent = uiModel.processedContent,
                                                        precomputedNode = uiModel.markdownNode,
                                                        timestamp = memo.timestamp,
                                                        dateFormat = dateFormat,
                                                        timeFormat = timeFormat,
                                                        tags = uiModel.tags,
                                                        onMenuClick = {
                                                            showMenu(com.lomo.ui.component.menu.MemoMenuState(
                                                                wordCount = memo.content.length,
                                                                createdTime = memo.timestamp.formatAsDateTime(dateFormat, timeFormat),
                                                                content = memo.content,
                                                                memo = memo
                                                            ))
                                                        },
                                                    menuContent = {}
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
        }
    }
}
