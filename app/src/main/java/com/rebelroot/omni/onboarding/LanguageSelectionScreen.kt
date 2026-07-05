/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.launch

// Soft light cyan/white gradient and slate-contrast tokens (copied from MyPdf UI pattern)
private val BgStart = Color(0xFFCBEFF4)
private val BgEnd = Color(0xFFFFFFFF)
private val AccentTeal = Color(0xFF0D9488)
private val DarkSlate = Color(0xFF07212F)
private val CardBg = Color.White
private val CardBorder = Color(0xFFCBD5E1)
private val SelectedBorder = Color(0xFF0D9488)
private val SubtextColor = Color(0xFF475569)

data class LanguageItem(
    val code: String,
    val nativeName: String,
    val englishName: String
)

private val supportedLanguages = listOf(
    LanguageItem("en", "English", "English"),
    LanguageItem("es", "Español", "Spanish"),
    LanguageItem("fr", "Français", "French"),
    LanguageItem("de", "Deutsch", "German"),
    LanguageItem("hi", "हिन्दी", "Hindi"),
    LanguageItem("pt", "Português", "Portuguese"),
    LanguageItem("ru", "Русский", "Russian"),
    LanguageItem("zh", "简体中文", "Chinese"),
    LanguageItem("ja", "日本語", "Japanese")
)

@Composable
fun LanguageSelectionScreen(
    viewModel: BrowserViewModel,
    context: android.content.Context,
    onFinish: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCode by remember { mutableStateOf(viewModel.selectedLanguageCode) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    // Subtle pulsing glow on the globe icon
    val infiniteTransition = rememberInfiniteTransition(label = "globe_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgStart, BgEnd)
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // --- Globe Icon with Glow ---
                Box(contentAlignment = Alignment.Center) {
                    // Glow ring
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = AccentTeal.copy(alpha = glowAlpha * 0.3f),
                                spotColor = AccentTeal.copy(alpha = glowAlpha * 0.5f)
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AccentTeal.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        AccentTeal.copy(alpha = 0.5f),
                                        AccentTeal.copy(alpha = 0.1f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Language",
                            tint = AccentTeal,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Title ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Choose Your Language",
                        color = DarkSlate,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can change this later in Settings",
                        color = SubtextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- Language List ---
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(supportedLanguages) { lang ->
                        val isSelected = selectedCode == lang.code

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedCode = lang.code },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) AccentTeal.copy(alpha = 0.08f) else CardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) SelectedBorder else CardBorder
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        color = if (isSelected) DarkSlate else DarkSlate.copy(alpha = 0.9f),
                                        fontSize = 16.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (lang.nativeName != lang.englishName) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = lang.englishName,
                                            color = if (isSelected) AccentTeal else SubtextColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(AccentTeal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Continue Button ---
                Button(
                    onClick = {
                        viewModel.saveLanguageSettings(context, selectedCode) {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .height(56.dp)
                        .shadow(8.dp, shape = CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSlate,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

