package com.dermoai.feature.faq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.faq.data.FaqEntry

private const val CATEGORY_ALL = "__all__"

private val CATEGORY_LABELS = mapOf(
    "skin_cancer" to "Skin cancer",
    "moles" to "Moles",
    "acne" to "Acne",
    "hair_loss" to "Hair loss",
    "nail_fungus" to "Nail fungus",
    "fungal" to "Fungal infections",
    "vascular" to "Vascular marks",
    "healthy_skin" to "Healthy skin",
    "app" to "About the app",
)

/**
 * Searchable FAQ (browse) + AI assistant (chat) in one screen.
 * The FAQ is curated, bundled, offline content about skin conditions,
 * prevention, and how the app works; the assistant chats via DeepSeek
 * using the user's own API key (configured in Settings).
 */
@Composable
fun FaqScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: FaqViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var tab by rememberSaveable { mutableStateOf(FaqTab.BROWSE) }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.faq_title),
            subtitle = stringResource(R.string.faq_subtitle),
        )
        MedicalDisclaimerBar()

        // FAQ / AI assistant toggle
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(
                label = stringResource(R.string.faq_tab_browse),
                selected = tab == FaqTab.BROWSE,
                onClick = { tab = FaqTab.BROWSE },
            )
            CategoryChip(
                label = stringResource(R.string.faq_tab_chat),
                selected = tab == FaqTab.CHAT,
                onClick = { tab = FaqTab.CHAT },
            )
        }

        when (tab) {
            FaqTab.CHAT -> ChatSection(Modifier.weight(1f), onOpenSettings = onOpenSettings)
            FaqTab.BROWSE -> BrowseContent(
                modifier = Modifier.weight(1f),
                query = query,
                onQueryChange = { query = it },
                category = category,
                onCategoryChange = { category = it },
                entries = entries,
                loading = loading,
                error = error,
                viewModel = viewModel,
            )
        }
    }
}

private enum class FaqTab { BROWSE, CHAT }

@Composable
private fun BrowseContent(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    entries: List<FaqEntry>,
    loading: Boolean,
    error: Boolean,
    viewModel: FaqViewModel,
) {
    Column(modifier) {
        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.faq_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )

        // Category chips
        val categories = remember(entries) {
            listOf(CATEGORY_ALL) + entries.map { it.category }.distinct().sorted()
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { cat ->
                CategoryChip(
                    label = if (cat == CATEGORY_ALL) stringResource(R.string.faq_all) else CATEGORY_LABELS[cat] ?: cat,
                    selected = cat == category,
                    onClick = { onCategoryChange(cat) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.faq_error), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.load() }) { Text(stringResource(R.string.faq_retry)) }
                }
            }
            else -> {
                val filtered = remember(entries, query, category) {
                    val byCategory = if (category == CATEGORY_ALL) entries else entries.filter { it.category == category }
                    viewModel.search(byCategory, query)
                }
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.faq_empty_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.faq_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            FaqCard(entry)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    NeuSurface(
        modifier = Modifier.padding(vertical = 4.dp),
        style = if (selected) NeuSurfaceStyle.Inset else NeuSurfaceStyle.Raised,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) DermoColors.TealAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) DermoColors.TealText else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FaqCard(entry: FaqEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.question,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse answer" else "Expand answer",
                    tint = DermoColors.TealAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = entry.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
