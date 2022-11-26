package com.maku.scratchcode.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.sharp.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maku.scratchcode.core.ScratchEntity
import com.maku.scratch.ui.components.ScratchTopAppBar
import com.maku.scratchcode.R
import com.maku.scratchcode.R.string.app_name
import com.maku.scratchcode.ui.components.OverlayLoadingWheel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScratchCodeApp(
    shouldShowCamera: MutableState<Boolean>,
    shouldShowProcessing: MutableState<Boolean>,
) {
    val online by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            ScratchTopAppBar(
                titleRes = app_name,
                navigationIcon = if (online) Icons.Default.Wifi else Icons.Default.WifiOff,
                navigationIconContentDescription = null,
                actionIcon = Icons.Outlined.Settings,
                actionIconContentDescription = stringResource(
                    id = app_name
                ),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                shouldShowCamera.value = true
            }) {
                Icon(imageVector = Icons.Sharp.Camera, contentDescription = "fab icon")
            }
        },
    ) {
        val code = mutableListOf<ScratchEntity>()
        code.add(ScratchEntity(1, "HGH62624", false))
        code.add(ScratchEntity(1, "HGH62623", false))
        code.add(ScratchEntity(1, "HGH62621", true))
        code.add(ScratchEntity(1, "HGH62622", true))
        code.add(ScratchEntity(1, "HGH62627", true))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = shouldShowProcessing.value,
                ) {
                    Row(  verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,) {
                        Text(
                            text = "Processing ...",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        AnimatedVisibility(
                            visible = shouldShowProcessing.value,
                        ) {
                            val loadingContentDescription = stringResource(R.string.app_name)
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OverlayLoadingWheel(
                                    modifier = Modifier.align(Alignment.Center).size(70.dp)
                                        .padding(5.dp),
                                    contentDesc = loadingContentDescription
                                )
                            }
                        }
                    }
                }
            }
            items(code) { model ->
                ListRow(model = model)
            }
        }
    }
}

@Composable
fun ListRow(model: ScratchEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = model.code,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Icon(
            imageVector = if (model.scanned) Icons.Outlined.Done else Icons.Outlined.Pending,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(70.dp)
                .padding(5.dp)
        )
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview("Scratch App")
@Composable
fun ScratchCodeAppPreview() {
    val shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
    val shouldShowProcessing: MutableState<Boolean> = mutableStateOf(false)

    ScratchCodeApp(shouldShowCamera, shouldShowProcessing)
}
