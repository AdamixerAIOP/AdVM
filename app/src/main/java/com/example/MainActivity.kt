package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthManager
import com.example.auth.authStateFlow
import com.example.data.LocalGuestRepository
import com.example.data.TutorialPreferences
import com.example.data.WorkstationRepository
import com.example.data.firestore.AdVmRepository
import com.example.data.model.UiState
import com.example.data.model.VmProfile
import com.example.service.GsiDownloadManager
import com.example.service.HostCapabilityProbe
import com.example.ui.components.TelemetryPill
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ImagesScreen
import com.example.ui.screens.TutorialSheet
import com.example.ui.screens.VncConsoleScreen
import com.example.ui.theme.AdVmTheme
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ConsoleViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ImagesViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_IMAGES = "images"
const val ROUTE_SYNC = "sync"
const val ROUTE_CONSOLE = "console"

private enum class TopTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Rounded.DeveloperBoard, "nav_tab_vms"),
    IMAGES(ROUTE_IMAGES, R.string.nav_images, Icons.Rounded.DownloadForOffline, "nav_tab_images"),
    SYNC(ROUTE_SYNC, R.string.nav_sync, Icons.Rounded.CloudSync, "nav_tab_sync")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdVmTheme {
                val context = LocalContext.current
                val safeAuth = remember(context) { AuthManager.safeFirebaseAuth(context) }
                AppNavigation(auth = safeAuth)
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth?) {
    val authFlow = remember(auth) { auth?.authStateFlow() ?: flowOf(null) }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    var isGuestMode by rememberSaveable { mutableStateOf(false) }
    val user = currentUser

    when {
        user != null -> {
            LaunchedEffect(user.uid) {
                isGuestMode = false
            }
            key(user.uid) {
                MainWorkstationShell(
                    user = user,
                    isGuestMode = false,
                    onExitGuestToSignIn = {}
                )
            }
        }
        isGuestMode -> {
            key("guest_local_session") {
                MainWorkstationShell(
                    user = null,
                    isGuestMode = true,
                    onExitGuestToSignIn = { isGuestMode = false }
                )
            }
        }
        else -> {
            AuthScreen(
                onAuthSuccess = { isGuestMode = false },
                onContinueAsGuest = { isGuestMode = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainWorkstationShell(
    user: FirebaseUser?,
    isGuestMode: Boolean,
    onExitGuestToSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUserId = user?.uid ?: "guest"

    val guestRepository = remember(isGuestMode) {
        if (isGuestMode) LocalGuestRepository() else null
    }

    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dash_${currentUserId}_$isGuestMode",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val repo: WorkstationRepository = guestRepository ?: run {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    AdVmRepository(FirebaseFirestore.getInstance(databaseId))
                }
                val probe = HostCapabilityProbe.inspect(app)
                DashboardViewModel(repo, currentUserId, probe, isGuestMode = isGuestMode)
            }
        }
    )

    val imagesViewModel: ImagesViewModel = viewModel(
        key = "img_${currentUserId}_$isGuestMode",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val repo: WorkstationRepository = guestRepository ?: run {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    AdVmRepository(FirebaseFirestore.getInstance(databaseId))
                }
                ImagesViewModel(repo, GsiDownloadManager(app), currentUserId)
            }
        }
    )

    val consoleViewModel: ConsoleViewModel = viewModel(key = "con_${currentUserId}_$isGuestMode")

    // Sync user session & starter VMs only when authenticated (never in guest mode)
    LaunchedEffect(user?.uid, isGuestMode) {
        if (user != null && !isGuestMode) {
            val dbId = context.getString(R.string.firestore_database_id)
            val repo = AdVmRepository(FirebaseFirestore.getInstance(dbId))
            repo.syncUserSession(user)
        }
    }

    val vmsState by dashboardViewModel.vmsState.collectAsStateWithLifecycle()
    val userProfileState by dashboardViewModel.userProfileState.collectAsStateWithLifecycle()
    val snapshotsState by dashboardViewModel.snapshotsState.collectAsStateWithLifecycle()
    val hostReport by dashboardViewModel.hostReport.collectAsStateWithLifecycle()
    val selectedFilter by dashboardViewModel.selectedFormFactorFilter.collectAsStateWithLifecycle()
    val selectedVmId by dashboardViewModel.selectedVmId.collectAsStateWithLifecycle()
    val dashBanner by dashboardViewModel.statusBanner.collectAsStateWithLifecycle()
    val isMutating by dashboardViewModel.isMutating.collectAsStateWithLifecycle()

    val imagesState by imagesViewModel.imagesState.collectAsStateWithLifecycle()
    val activeDownload by imagesViewModel.activeDownload.collectAsStateWithLifecycle()
    val imagesBanner by imagesViewModel.statusBanner.collectAsStateWithLifecycle()

    var activeConsoleVm by remember { mutableStateOf<VmProfile?>(null) }
    val tutorialPrefs = remember(context) { TutorialPreferences(context.applicationContext) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!tutorialPrefs.hasSeenTutorial.first()) {
            showTutorial = true
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD
    val isConsoleRoute = currentRoute.startsWith(ROUTE_CONSOLE)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
    ) {
        val useRail = maxWidth >= 600.dp
        val isExpandedWidth = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && !isConsoleRoute) {
                NavigationRail(
                    containerColor = SlateSurface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TopTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = TelemetryCyan,
                                selectedTextColor = TelemetryCyan,
                                indicatorColor = SlateElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = ObsidianCanvas,
                topBar = {
                    if (!isConsoleRoute) {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    TelemetryPill(
                                        label = if (isGuestMode) "CLOUD OFF" else "SYNC ON",
                                        accent = if (isGuestMode) AmberWarn else AndroidEmerald,
                                        leadingDot = true
                                    )
                                    IconButton(
                                        onClick = { showTutorial = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("open_tutorial_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                            contentDescription = stringResource(R.string.action_open_tutorial),
                                            tint = TelemetryCyan
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = SlateSurface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && !isConsoleRoute) {
                        NavigationBar(
                            containerColor = SlateSurface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            TopTab.entries.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TelemetryCyan,
                                        selectedTextColor = TelemetryCyan,
                                        indicatorColor = SlateElevated,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(ROUTE_DASHBOARD) {
                        DashboardScreen(
                            vmsState = vmsState,
                            hostReport = hostReport,
                            selectedFilter = selectedFilter,
                            selectedVmId = selectedVmId,
                            statusBanner = dashBanner,
                            isMutating = isMutating,
                            isExpandedWidth = isExpandedWidth,
                            onRefreshProbe = { dashboardViewModel.refreshHostProbe(context) },
                            onFilterChange = { dashboardViewModel.setFormFactorFilter(it) },
                            onSelectVm = { dashboardViewModel.selectVm(it) },
                            onSaveVm = { vm, done -> dashboardViewModel.saveVm(vm, done) },
                            onTogglePower = { dashboardViewModel.toggleVmPower(it) },
                            onStopVm = { dashboardViewModel.stopVm(it) },
                            onDeleteVm = { dashboardViewModel.deleteVm(it) },
                            onCreateSnapshot = { vm, label -> dashboardViewModel.createSnapshot(vm, label) },
                            onLaunchConsole = { vm ->
                                activeConsoleVm = vm
                                navController.navigate(ROUTE_CONSOLE)
                            },
                            onClearBanner = { dashboardViewModel.clearBanner() }
                        )
                    }

                    composable(ROUTE_IMAGES) {
                        ImagesScreen(
                            imagesState = imagesState,
                            activeDownload = activeDownload,
                            statusBanner = imagesBanner,
                            hostReport = hostReport,
                            isMutatingVm = isMutating,
                            onStartCustomDownload = { title, url, fam, ver, maj, fmt, arch ->
                                imagesViewModel.startCustomDownload(title, url, fam, ver, maj, fmt, arch)
                            },
                            onCancelDownload = { imagesViewModel.cancelActiveDownload() },
                            onRegisterTemplate = { imagesViewModel.registerTemplate(it) },
                            onImportLocalUri = { uri, fam, ver, maj ->
                                imagesViewModel.importLocalDocument(uri, fam, ver, maj)
                            },
                            onDeleteImage = { imagesViewModel.deleteImage(it) },
                            onCreateVmFromImage = { vm, done ->
                                dashboardViewModel.saveVm(vm) {
                                    done()
                                    navController.navigate(ROUTE_DASHBOARD) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onClearBanner = { imagesViewModel.clearBanner() }
                        )
                    }

                    composable(ROUTE_SYNC) {
                        CloudSyncScreen(
                            isGuestMode = isGuestMode,
                            userProfileState = userProfileState,
                            snapshotsState = snapshotsState,
                            onDeleteSnapshot = { dashboardViewModel.deleteSnapshot(it) },
                            onExitGuestToSignIn = onExitGuestToSignIn,
                            onSignOut = {
                                AuthManager.signOut(credentialManager, scope)
                            },
                            onOpenTutorial = { showTutorial = true }
                        )
                    }

                    composable(ROUTE_CONSOLE) {
                        val vmList = (vmsState as? UiState.Success)?.data.orEmpty()
                        val targetVm = activeConsoleVm ?: vmList.firstOrNull()
                        if (targetVm != null) {
                            VncConsoleScreen(
                                vm = targetVm,
                                hostReport = hostReport,
                                consoleViewModel = consoleViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTutorial) {
        TutorialSheet(
            onDismiss = {
                showTutorial = false
                scope.launch { tutorialPrefs.setTutorialCompleted(true) }
            },
            onJumpToRoute = { targetRoute ->
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
