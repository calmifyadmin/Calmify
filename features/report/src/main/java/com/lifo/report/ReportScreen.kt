package com.lifo.report

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
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import coil.transform.CircleCropTransformation
import com.lifo.mongo.repository.Diaries
import com.lifo.util.Screen
import com.lifo.util.model.RequestState
import java.time.ZonedDateTime
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun ReportScreen(
    diaries: Diaries,
    drawerState: DrawerState,
    onMenuClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit,
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    dateIsSelected: Boolean,
    onDateSelected: (ZonedDateTime) -> Unit,
    onDateReset: () -> Unit,
    viewModel: ReportViewModel, // Assicurati di passare il ViewModel
    userProfileImageUrl: String?,
    navigateToHome: () -> Unit
    // Assicurati di passare il ViewModel
) {

    val durationMillis = 1000 // specify your desired duration in milliseconds
    var padding by remember { mutableStateOf(PaddingValues()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = rememberNavController()
    val fabHeight = 100.dp //FabSize+Padding
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
    var selectedItem by remember { mutableStateOf(NavigationItem.Report)}
    val bottomBarVisibleState = remember { mutableStateOf(true) }
    var isVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dragAmount ->
            when {
                dragAmount > 0 -> isVisible = false // Swipe down, hide the bar
                dragAmount < 0 -> isVisible = true  // Swipe up, show the bar
            }
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
                painter = rememberImagePainter(data = userProfileImageUrl), // Use Coil's rememberImagePainter to load the image from the URL
                contentDescription = "User Profile Image"
            )
            // ...
        }
    )
    NavigationDrawer(
        drawerState = drawerState,
        onSignOutClicked = onSignOutClicked,
        onDeleteAllClicked = onDeleteAllClicked,
        userProfileImageUrl = userProfileImageUrl
    ) {

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).nestedScroll(nestedScrollConnection),
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route


                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = -fabOffsetHeightPx.value.roundToInt()) }
                        // Applica il gradiente dal basso verso l'alto
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background, // Più scuro in basso
                                    Color.Transparent // Trasparente verso l'alto
                                ),
                                startY = 400f, // Puoi modificare questi valori per regolare l'estensione della sfumatura
                                endY = 0f
                            )
                        )
                ) {
                    NavigationBar(

                        containerColor = Color.Transparent // Usa un colore trasparente per il container
                    ) {
                        NavigationItem.values().forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.name) },
                                selected = selectedItem == item,
                                onClick = {
                                    when (item) {
                                        NavigationItem.Home -> {
                                            selectedItem = NavigationItem.Home
                                            navigateToHome();
                                        }
                                        NavigationItem.Personal -> {
                                            selectedItem = NavigationItem.Personal
                                        }

                                        else -> {
                                            selectedItem = NavigationItem.Report
                                        }
                                    }
                                },
                                
                            )
                        }
                    }
                }
            }


            ,

            topBar = {
                ReportTopBar(
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
                        .padding(
                            end = padding.calculateEndPadding(LayoutDirection.Ltr)
                        ),
                    onClick = navigateToWrite
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Diary Icon"
                    )
                }
            },
            content = {
                padding = it
                when (diaries) {
                    is RequestState.Success -> {
                        MissingPermissionsComponent {
                            ReportContent(
                                paddingValues = it,
                                diaryNotes = diaries.data,
                                onClick = navigateToWriteWithArgs,
                                isLoading = false,
                                viewModel = viewModel // Passa il ViewModel
                            )
                        }
                    }
                    is RequestState.Error -> {
                        EmptyPage(
                            title = "Error",
                            subtitle = diaries.error.message ?: "Unknown error"
                        )
                    }
                    is RequestState.Loading -> {
                        ShimmeredPage(it) // Mostra la pagina shimmer durante il caricamento
                    }
                    else -> {
                        ReportContent(
                            modifier = Modifier

                                .verticalScroll(rememberScrollState()),
                            paddingValues = it,
                            diaryNotes = null,
                            onClick = navigateToWriteWithArgs,
                            isLoading = true,
                            viewModel = viewModel // Passa il ViewModel
                        )// Qui puoi gestire eventuali altri stati, o lasciare vuoto se non necessario
                    }
                }
            }
        )
    }
}
enum class NavigationItem(val route: String, val icon: ImageVector) {
    Home("checkRoute", Icons.Outlined.CheckCircle),
    Report("reportsRoute", Icons.Outlined.Face),  // sostituisci con il nome effettivo della rotta
    Personal("personalRoute", Icons.Outlined.Info)     // sostituisci con il nome effettivo della rotta
}
@Composable
fun BottomNavigationBar(navController: NavHostController, show:Boolean){
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
                        //navController.navigate(item.route) {
                        //    // Configura il comportamento di navigazione qui
                        //}
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
                        modifier = Modifier
                            .align(Alignment.Start)// Vertically center the content of the row
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

                            .size(100.dp) // Set a specific size for a small profile picture
                            .clip(CircleShape) // Clip the image to a circle shape to make it rounded
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape) // Optional: Add a border to the circle if desired
                            .padding(4.dp) // Optional: Add padding around the image if needed
                            .align(Alignment.CenterHorizontally),
                        // Center the image horizontally in its container
                        painter = rememberImagePainter(
                            data = userProfileImageUrl,
                            builder = {
                                transformations(CircleCropTransformation()) // Use Coil's CircleCropTransformation for rounding
                            }
                        ), // Use Coil's rememberImagePainter to load the image from the URL
                        contentDescription = "User Profile Image"
                    )

                    NavigationDrawerItem(
                        modifier= Modifier.padding(top = 24.dp),
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