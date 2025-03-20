package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.rememberImagePainter
import coil.transform.CircleCropTransformation
import com.lifo.mongo.repository.Diaries
import com.lifo.util.Screen
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun HomeScreen(
    diaries: Diaries,
    navController: NavHostController, // NavController globale
    drawerState: DrawerState,
    onMenuClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit,
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    dateIsSelected: Boolean,
    onDateSelected: (ZonedDateTime) -> Unit,
    onDateReset: () -> Unit,
    viewModel: HomeViewModel, // Passa il ViewModel
    userProfileImageUrl: String?,
    navigateToReport: () -> Unit
) {
    // Forza il refresh dei dati al caricamento della schermata
    LaunchedEffect(Unit) {
        viewModel.fetchDiaries()
    }

    var padding by remember { mutableStateOf(PaddingValues()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fabHeight = 100.dp
    val fabHeightPx = with(LocalDensity.current) { fabHeight.roundToPx().toFloat() }
    val fabOffsetHeightPx = remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = fabOffsetHeightPx.value + delta
                fabOffsetHeightPx.value = newOffset.coerceIn(-fabHeightPx, 0f)
                return Offset.Zero
            }
        }
    }
    var selectedItem by remember { mutableStateOf(NavigationItem.Home) }
    var isVisible by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dragAmount ->
            if (dragAmount > 0) isVisible = false else if (dragAmount < 0) isVisible = true
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { scroll ->
            isVisible = scroll <= scrollState.value
        }
    }
    ModalDrawerSheet(
        content = {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                painter = rememberImagePainter(data = userProfileImageUrl),
                contentDescription = "User Profile Image"
            )
        }
    )
    NavigationDrawer(
        drawerState = drawerState,
        onSignOutClicked = onSignOutClicked,
        onDeleteAllClicked = onDeleteAllClicked,
        userProfileImageUrl = userProfileImageUrl
    ) {
        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(nestedScrollConnection),
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = -fabOffsetHeightPx.value.roundToInt()) }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    Color.Transparent
                                ),
                                startY = 180f,
                                endY = 0f
                            )
                        )
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent
                    ) {
                        NavigationItem.values().forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.name) },
                                selected = selectedItem == item,
                                onClick = {
                                    when (item) {
                                        NavigationItem.Reports -> {
                                            selectedItem = NavigationItem.Reports
                                            navigateToReport()
                                        }
                                        NavigationItem.Personal -> {
                                            selectedItem = NavigationItem.Personal
                                        }
                                        else -> {
                                            selectedItem = NavigationItem.Home
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            topBar = {
                HomeTopBar(
                    scrollBehavior = scrollBehavior,
                    onMenuClicked = onMenuClicked,
                    dateIsSelected = dateIsSelected,
                    onDateSelected = onDateSelected,
                    onDateReset = onDateReset,
                    userProfileImageUrl = userProfileImageUrl
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = -fabOffsetHeightPx.value.roundToInt()) }
                        .padding(end = padding.calculateEndPadding(LayoutDirection.Ltr)),
                    onClick = navigateToWrite
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Diary Icon"
                    )
                }
            },
            content = { padding ->
                when (diaries) {
                    is RequestState.Success -> {
                        MissingPermissionsComponent {
                            HomeContent(
                                paddingValues = padding,
                                diaryNotes = diaries.data, // diariesState.data è di tipo Diaries (Map<LocalDate, List<Diary>>?)
                                onClick = navigateToWriteWithArgs,
                                isLoading = false,
                                viewModel = viewModel
                            )
                        }
                    }
                    is RequestState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Errore: ${diaries.error.message}")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.fetchDiaries() }) {
                                    Text("Riprova")
                                }
                            }
                        }
                    }
                    is RequestState.Loading -> {
                        LoadingScreen(modifier = Modifier.fillMaxSize())
                    }
                    else -> {
                        // Stato Idle o altro: mostra il loader per sicurezza
                        LoadingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        )
    }
}

enum class NavigationItem(val route: String, val icon: ImageVector) {
    Home("checkRoute", Icons.Outlined.CheckCircle),
    Reports("reportsRoute", Icons.Outlined.Face),
    Personal("personalRoute", Icons.Outlined.Info)
}

@Composable
fun BottomNavigationBar(navController: NavHostController, show: Boolean) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    if (show) {
        NavigationBar {
            NavigationItem.values().forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.name) },
                    selected = currentRoute == item.route,
                    onClick = {
                        // Implementa la navigazione se necessario
                    }
                )
            }
        }
    }
}

@Composable
internal fun NavigationDrawer(
    drawerState: DrawerState,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit,
    userProfileImageUrl: String?,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                content = {
                    Row(
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Image(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                            contentDescription = "Logo Image"
                        )
                    }
                    Image(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(4.dp)
                            .align(Alignment.CenterHorizontally),
                        painter = rememberImagePainter(
                            data = userProfileImageUrl,
                            builder = {
                                transformations(CircleCropTransformation())
                            }
                        ),
                        contentDescription = "User Profile Image"
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.padding(top = 24.dp),
                        label = {
                            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                                Icon(
                                    painter = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                                    contentDescription = "Google Logo",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sign Out",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        selected = false,
                        onClick = onSignOutClicked
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete All Icon",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Delete All Diaries",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        selected = false,
                        onClick = onDeleteAllClicked
                    )
                }
            )
        },
        content = content
    )
}
