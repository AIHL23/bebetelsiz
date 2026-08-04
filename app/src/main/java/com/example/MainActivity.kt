package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.ChildProfileEntity
import com.example.location.GpsLocationTracker
import com.example.location.LiveGpsLocation
import com.example.network.WebRtcSignalingManager
import com.example.network.WebRtcStatus
import com.example.service.ParentalMonitoringService
import com.example.ui.components.LiveCameraPreview
import com.example.ui.components.WebRtcVideoPlayer
import com.example.viewmodel.BabyMonitorViewModel
import com.example.model.WebRtcConnectionState
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ParentalControlApp()
      }
    }
  }
}

data class KidsVideo(
  val id: String,
  val title: String,
  val channel: String,
  val views: String,
  val duration: String,
  val category: String,
  val iconBg: Color,
  val icon: ImageVector
)

data class ActivityItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val time: String
)

enum class AppMode {
  KIDS_MODE,
  ADMIN_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlApp() {
  val context = LocalContext.current
  val db = remember { AppDatabase.getDatabase(context) }
  val coroutineScope = rememberCoroutineScope()

  var appMode by remember { mutableStateOf(AppMode.KIDS_MODE) }
  var searchQuery by remember { mutableStateOf("") }
  var isPermissionsGranted by remember { mutableStateOf(false) }
  var showPermissionDialog by remember { mutableStateOf(true) }

  // Child Profile & WebRTC Pairing State
  var childNameInput by remember { mutableStateOf("") }
  var pairingCodeInput by remember { mutableStateOf("") }
  var registeredChildName by remember { mutableStateOf<String?>(null) }
  var registeredPairingCode by remember { mutableStateOf<String?>(null) }
  var showNamePromptDialog by remember { mutableStateOf(false) }
  var liveClockString by remember { mutableStateOf("") }

  // Foreground service & GPS location state
  val gpsTracker = remember { GpsLocationTracker(context) }
  var currentGpsLocation by remember { mutableStateOf(LiveGpsLocation()) }

  LaunchedEffect(isPermissionsGranted) {
    if (isPermissionsGranted) {
      try {
        ParentalMonitoringService.startService(context)
        gpsTracker.startLocationUpdates()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  LaunchedEffect(Unit) {
    gpsTracker.locationState.collect { location ->
      currentGpsLocation = location
    }
  }

  // Observe Saved Child Profile from Database
  LaunchedEffect(Unit) {
    db.childProfileDao().getLatestProfile().collect { profile ->
      if (profile != null) {
        registeredChildName = profile.childName
        registeredPairingCode = profile.pairingCode
        WebRtcSignalingManager.registerPairingCode(profile.pairingCode, profile.childName)
      } else {
        showNamePromptDialog = true
      }
    }
  }

  // Foreground clock notification ticker loop
  LaunchedEffect(Unit) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    while (true) {
      liveClockString = timeFormat.format(Date())
      delay(1000L)
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissionsMap ->
    val cameraGranted = permissionsMap[Manifest.permission.CAMERA] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    val micGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    val locationGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    
    isPermissionsGranted = cameraGranted && micGranted && locationGranted
    showPermissionDialog = !isPermissionsGranted
  }

  LaunchedEffect(Unit) {
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (cameraGranted && micGranted && locationGranted) {
      isPermissionsGranted = true
      showPermissionDialog = false
    }
  }

  // Welcome / Child Name & Pairing Code Dialog
  if (showNamePromptDialog) {
    AlertDialog(
      onDismissRequest = { },
      containerColor = DarkSurface,
      shape = RoundedCornerShape(28.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.ChildCare,
          contentDescription = null,
          tint = Color(0xFFFFB74D),
          modifier = Modifier.size(42.dp)
        )
      },
      title = {
        Text(
          text = "Merhaba Küçük Adam!",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary
          ),
          textAlign = TextAlign.Center
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "İsmini ve Ebeveyn Panelinden aldığın Eşleştirme Kodunu gir:",
            style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary),
            textAlign = TextAlign.Center
          )
          OutlinedTextField(
            value = childNameInput,
            onValueChange = { childNameInput = it },
            placeholder = { Text("İsmin nedir?") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = DarkSurfaceVariant,
              unfocusedContainerColor = DarkSurfaceVariant,
              focusedBorderColor = Color(0xFFFFB74D),
              unfocusedBorderColor = DarkBorder,
              focusedTextColor = DarkTextPrimary,
              unfocusedTextColor = DarkTextPrimary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("child_name_input_field")
          )

          OutlinedTextField(
            value = pairingCodeInput,
            onValueChange = { pairingCodeInput = it },
            placeholder = { Text("Eşleştirme Kodu (Örn: 582914)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = DarkSurfaceVariant,
              unfocusedContainerColor = DarkSurfaceVariant,
              focusedBorderColor = DarkPrimary,
              unfocusedBorderColor = DarkBorder,
              focusedTextColor = DarkTextPrimary,
              unfocusedTextColor = DarkTextPrimary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pairing_code_input_field")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (childNameInput.isNotBlank() && pairingCodeInput.isNotBlank()) {
              val name = childNameInput.trim()
              val code = pairingCodeInput.trim()
              val deviceId = UUID.randomUUID().toString().take(8)
              coroutineScope.launch {
                db.childProfileDao().insertProfile(
                  ChildProfileEntity(
                    childName = name,
                    pairingCode = code,
                    deviceId = "DEV-$deviceId",
                    deviceModel = android.os.Build.MODEL ?: "Android"
                  )
                )
                WebRtcSignalingManager.registerPairingCode(code, name)
                registeredChildName = name
                registeredPairingCode = code
                showNamePromptDialog = false
              }
            }
          },
          enabled = childNameInput.isNotBlank() && pairingCodeInput.isNotBlank(),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFB74D),
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_child_name_button")
        ) {
          Text("Eşleştir ve Başlat", fontWeight = FontWeight.Bold)
        }
      }
    )
  }

  // Kids Video Player state
  var selectedCategory by remember { mutableStateOf("Tümü") }
  var activePlayingVideo by remember { mutableStateOf<KidsVideo?>(null) }

  // Admin Dashboard State
  var selectedTab by remember { mutableIntStateOf(0) }
  var isCameraConnected by remember { mutableStateOf(false) }
  var isMicActive by remember { mutableStateOf(true) }

  val kidsVideos = remember {
    listOf(
      KidsVideo("1", "Kırmızı Balık Gölde Kıvrıla Kıvrıla Yüzüyor - Eğitici Çocuk Şarkısı", "Çocuk Masalları TV", "1.2M görüntüleme", "03:45", "Şarkılar", Color(0xFFFF7043), Icons.Default.MusicNote),
      KidsVideo("2", "Sevimli Dostlar ile Sayıları Öğrenelim 1-10", "Eğitici Çizgi Film", "850B görüntüleme", "12:10", "Eğitim", Color(0xFF42A5F5), Icons.Default.School),
      KidsVideo("3", "Ali Babanın Bir Çiftliği Var - Bebek İlahileri & Şarkıları", "Bebek Dünyası", "3.4M görüntüleme", "05:20", "Şarkılar", Color(0xFF66BB6A), Icons.Default.Pets),
      KidsVideo("4", "Küçük Tay ve Orman Macera Masalı", "Masal Diyarı", "520B görüntüleme", "15:30", "Masal", Color(0xFFAB47BC), Icons.Default.AutoAwesome),
      KidsVideo("5", "Renkleri ve Şekilleri Öğreniyorum - Okul Öncesi Çizgi Dizi", "Neşeli Çocuklar", "2.1M görüntüleme", "08:15", "Çizgi Film", Color(0xFFFFA726), Icons.Default.SmartDisplay),
      KidsVideo("6", "Araba Yarışı ve İtfaiye Kamyonu Eğlencesi", "Oyun Parkı TV", "980B görüntüleme", "06:50", "Çizgi Film", Color(0xFFEC407A), Icons.Default.DirectionsCar)
    )
  }

  val activityLogs = remember {
    mutableStateListOf(
      ActivityItem("1", "Gizli Kamera Bağlantısı", "Ebeveyn izleme isteği tamamlandı", Icons.Default.Videocam, "Şimdi"),
      ActivityItem("2", "Ortam Mikrofonu Dinleme", "Arka plan ses analizi aktif", Icons.Default.Mic, "09:42"),
      ActivityItem("3", "Konum Güncellendi", "Bebek Cihazı Ev Bölgesinde", Icons.Default.LocationOn, "09:15"),
      ActivityItem("4", "Cihaz Durumu", "Pil Durumu %84 • Arka Plan Modu", Icons.Default.BatteryChargingFull, "08:30")
    )
  }

  val categories = listOf("Tümü", "Çizgi Film", "Şarkılar", "Eğitim", "Masal")

  // Function to check search input trigger for "admin" keyword
  fun checkSearchTrigger(query: String) {
    if (query.trim().lowercase() == "admin") {
      appMode = AppMode.ADMIN_MODE
      searchQuery = ""
    }
  }

  // Initial Startup Permission Dialog
  if (showPermissionDialog && !isPermissionsGranted) {
    AlertDialog(
      onDismissRequest = { },
      containerColor = DarkSurface,
      shape = RoundedCornerShape(28.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = DarkPrimary,
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text(
          text = "Uygulama İzinleri Gerekli",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary
          ),
          textAlign = TextAlign.Center
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "Uygulamanın kesintisiz ve arka planda güvenli çalışabilmesi için aşağıdaki izinler istenmektedir:",
            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
          )

          PermissionItem(
            icon = Icons.Default.Videocam,
            title = "Kamera İzni",
            description = "Canlı video izleme ve arka plan kamera kontrolü için"
          )
          PermissionItem(
            icon = Icons.Default.Mic,
            title = "Mikrofon İzni",
            description = "Ortam seslerini dinleme ve ses iletimi için"
          )
          PermissionItem(
            icon = Icons.Default.LocationOn,
            title = "Konum İzni",
            description = "Cihazın anlık konumunu haritada takip etmek için"
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val perms = mutableListOf(
              Manifest.permission.CAMERA,
              Manifest.permission.RECORD_AUDIO,
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
              perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = DarkPrimary,
            contentColor = DarkOnPrimary
          ),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("grant_permissions_button")
        ) {
          Text("Tüm İzinleri Ver ve Başlat", fontWeight = FontWeight.Bold)
        }
      }
    )
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = DarkBackground,
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = if (appMode == AppMode.ADMIN_MODE) Icons.Default.AdminPanelSettings else Icons.Default.ChildCare,
              contentDescription = null,
              tint = if (appMode == AppMode.ADMIN_MODE) DarkPrimary else Color(0xFFFFB74D)
            )
            Text(
              text = if (appMode == AppMode.ADMIN_MODE) "Ebeveyn Kontrol Paneli" else "Çocuk YouTube TV",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary
              )
            )
          }
        },
        actions = {
          if (appMode == AppMode.ADMIN_MODE) {
            TextButton(
              onClick = { appMode = AppMode.KIDS_MODE },
              colors = ButtonDefaults.textButtonColors(contentColor = DarkPrimary)
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Çocuk Moduna Dön", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          } else {
            IconButton(
              onClick = {
                val perms = mutableListOf(
                  Manifest.permission.CAMERA,
                  Manifest.permission.RECORD_AUDIO,
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                  perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(perms.toTypedArray())
              }
            ) {
              Icon(
                imageVector = if (isPermissionsGranted) Icons.Default.VerifiedUser else Icons.Default.Warning,
                contentDescription = "İzin Durumu",
                tint = if (isPermissionsGranted) StatusGreen else LiveIndicatorRed
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
      )
    },
    bottomBar = {
      if (appMode == AppMode.ADMIN_MODE) {
        NavigationBar(
          containerColor = Color(0xFF1C1B1F),
          tonalElevation = 0.dp
        ) {
          NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Panel") },
            label = { Text("Panel", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = DarkPrimary,
              selectedTextColor = DarkPrimary,
              indicatorColor = DarkSurfaceVariant,
              unselectedIconColor = DarkTextSecondary,
              unselectedTextColor = DarkTextSecondary
            ),
            modifier = Modifier.testTag("nav_panel")
          )
          NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = "Harita") },
            label = { Text("Harita", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = DarkPrimary,
              selectedTextColor = DarkPrimary,
              indicatorColor = DarkSurfaceVariant,
              unselectedIconColor = DarkTextSecondary,
              unselectedTextColor = DarkTextSecondary
            ),
            modifier = Modifier.testTag("nav_harita")
          )
          NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            icon = { Icon(Icons.Default.History, contentDescription = "Geçmiş") },
            label = { Text("Geçmiş", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = DarkPrimary,
              selectedTextColor = DarkPrimary,
              indicatorColor = DarkSurfaceVariant,
              unselectedIconColor = DarkTextSecondary,
              unselectedTextColor = DarkTextSecondary
            ),
            modifier = Modifier.testTag("nav_gecmis")
          )
          NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
            label = { Text("Ayarlar", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = DarkPrimary,
              selectedTextColor = DarkPrimary,
              indicatorColor = DarkSurfaceVariant,
              unselectedIconColor = DarkTextSecondary,
              unselectedTextColor = DarkTextSecondary
            ),
            modifier = Modifier.testTag("nav_ayarlar")
          )
        }
      }
    }
  ) { innerPadding ->
    // Animated Mode Switcher
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Background Service Live Notification Banner (Foreground Clock Ticker)
      Surface(
        color = Color(0xFF1E2638),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(StatusGreen)
            )
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
            Text(
              text = if (registeredChildName != null) "Cihaz: $registeredChildName • WebRTC (Vercel) Bağlı" else "Bebek Telefonu ⇄ WebRTC (Vercel) ⇄ Ebeveyn",
              style = MaterialTheme.typography.labelSmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.SemiBold)
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .background(DarkSurface, RoundedCornerShape(12.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(14.dp))
            Text(
              text = liveClockString.ifEmpty { "23:49:21" },
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
            )
          }
        }
      }

      AnimatedContent(
        targetState = appMode,
        transitionSpec = {
          fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it / 2 }) togetherWith
              fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it / 2 })
        },
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
      ) { currentMode ->
        when (currentMode) {
          AppMode.KIDS_MODE -> {
            KidsVideoScreen(
              searchQuery = searchQuery,
              onSearchQueryChange = { query ->
                searchQuery = query
                checkSearchTrigger(query)
              },
              onSearchSubmitted = { checkSearchTrigger(searchQuery) },
              selectedCategory = selectedCategory,
              onCategorySelected = { selectedCategory = it },
              categories = categories,
              videos = kidsVideos,
              activePlayingVideo = activePlayingVideo,
              onVideoSelect = { activePlayingVideo = it },
              onClosePlayer = { activePlayingVideo = null }
            )
          }

          AppMode.ADMIN_MODE -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              // Admin Search / Command Bar
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cihaz veya komut ara...", color = DarkTextMuted, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = DarkSurface,
                  unfocusedContainerColor = DarkSurface,
                  disabledContainerColor = DarkSurface,
                  focusedBorderColor = DarkBorder,
                  unfocusedBorderColor = DarkBorder.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("search_command_bar")
              )

              when (selectedTab) {
                0 -> DashboardScreen(
                  isConnected = isCameraConnected,
                  isMicActive = isMicActive,
                  onConnectToggle = {
                    isCameraConnected = !isCameraConnected
                    if (isCameraConnected) {
                      activityLogs.add(0, ActivityItem(System.currentTimeMillis().toString(), "Kamera Açıldı", "Canlı akış başlatıldı", Icons.Default.Videocam, "Şimdi"))
                    }
                  },
                  onMicToggle = { isMicActive = !isMicActive },
                  activityLogs = activityLogs
                )
                1 -> LocationMapScreen(currentGpsLocation)
                2 -> HistoryLogsScreen(activityLogs)
                3 -> SettingsScreen()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PermissionItem(
  icon: ImageVector,
  title: String,
  description: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
      .padding(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(DarkPrimary.copy(alpha = 0.2f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(20.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary))
      Text(description, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextMuted, fontSize = 11.sp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsVideoScreen(
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSearchSubmitted: () -> Unit,
  selectedCategory: String,
  onCategorySelected: (String) -> Unit,
  categories: List<String>,
  videos: List<KidsVideo>,
  activePlayingVideo: KidsVideo?,
  onVideoSelect: (KidsVideo) -> Unit,
  onClosePlayer: () -> Unit
) {
  val filteredVideos = remember(searchQuery, selectedCategory, videos) {
    videos.filter { video ->
      val matchesCategory = selectedCategory == "Tümü" || video.category.equals(selectedCategory, ignoreCase = true)
      val matchesSearch = searchQuery.isBlank() || video.title.contains(searchQuery, ignoreCase = true)
      matchesCategory && matchesSearch
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Search bar for kids
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = DarkSurface),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFB74D)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.ChildCare, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Hoş Geldin Küçük Adam!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary))
          Text("Sana özel çizgi filmleri izleyebilirsin", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
        }
      }
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      placeholder = { Text("Aramak istediğin çizgi filmi yaz...", color = DarkTextMuted, fontSize = 14.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFFFB74D)) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchQueryChange("") }) {
            Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = DarkTextMuted)
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(28.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = DarkSurface,
        unfocusedContainerColor = DarkSurface,
        disabledContainerColor = DarkSurface,
        focusedBorderColor = Color(0xFFFFB74D),
        unfocusedBorderColor = DarkBorder
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("kids_search_bar")
    )

    // Active Video Modal Overlay
    activePlayingVideo?.let { playingVideo ->
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("video_player_card")
      ) {
        Column {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp)
              .background(
                Brush.verticalGradient(
                  colors = listOf(playingVideo.iconBg.copy(alpha = 0.8f), Color.Black)
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = playingVideo.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                  .background(Color.Red, RoundedCornerShape(12.dp))
                  .padding(horizontal = 10.dp, vertical = 4.dp)
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("OYNATILIYOR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            IconButton(
              onClick = onClosePlayer,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
            }
          }

          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = playingVideo.title,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "${playingVideo.channel} • ${playingVideo.views}",
              style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
            )
          }
        }
      }
    }

    // Category Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(categories) { category ->
        val isSelected = category == selectedCategory
        FilterChip(
          selected = isSelected,
          onClick = { onCategorySelected(category) },
          label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFFFB74D),
            selectedLabelColor = Color.Black,
            containerColor = DarkSurface,
            labelColor = DarkTextPrimary
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = DarkBorder
          ),
          shape = RoundedCornerShape(20.dp)
        )
      }
    }

    // Video Feed
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      items(filteredVideos) { video ->
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = DarkSurface),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onVideoSelect(video) }
            .testTag("kids_video_item_${video.id}")
        ) {
          Column {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(video.iconBg.copy(alpha = 0.25f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = video.icon,
                contentDescription = null,
                tint = video.iconBg,
                modifier = Modifier.size(56.dp)
              )

              // Duration Badge
              Box(
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(10.dp)
                  .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(video.duration, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
              }

              // Play Button Overlay
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Color.Red.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.White, modifier = Modifier.size(28.dp))
              }
            }

            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.Top
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(video.iconBg),
                contentAlignment = Alignment.Center
              ) {
                Icon(video.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = video.title,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${video.channel} • ${video.views}",
                  style = MaterialTheme.typography.bodySmall.copy(color = DarkTextMuted, fontSize = 11.sp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun DashboardScreen(
  isConnected: Boolean,
  isMicActive: Boolean,
  onConnectToggle: () -> Unit,
  onMicToggle: () -> Unit,
  activityLogs: List<ActivityItem>
) {
  var generatedPairingCode by remember { mutableStateOf("582914") }
  val activePairingCode by WebRtcSignalingManager.activePairingCode.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // WebRTC Pairing Code Generator Banner for Admin
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("webrtc_pairing_card")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.QrCode, contentDescription = null, tint = DarkPrimary)
              Text("Ebeveyn Eşleştirme Kodu", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
            }
            Text("Vercel Signaling", style = MaterialTheme.typography.labelSmall.copy(color = StatusGreen, fontWeight = FontWeight.Bold))
          }

          Text(
            text = "Bebek telefonunda ilk açılışta aşağıdaki kodu 'Eşleştirme Kodu' alanına girin:",
            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .border(1.dp, DarkPrimary, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
              Text(
                text = activePairingCode.ifEmpty { generatedPairingCode },
                style = MaterialTheme.typography.headlineMedium.copy(
                  color = DarkPrimary,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 4.sp
                )
              )
            }

            Button(
              onClick = {
                val newCode = WebRtcSignalingManager.generatePairingCode()
                generatedPairingCode = newCode
              },
              colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary, contentColor = DarkOnPrimary),
              shape = RoundedCornerShape(16.dp)
            ) {
              Text("Yeni Kod Al", fontWeight = FontWeight.Bold)
            }
          }

          // Architecture Flow Diagram Indicator
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Bebek Telefonu ⇄ WebRTC (Vercel) ⇄ Ebeveyn Telefonu",
              style = MaterialTheme.typography.labelSmall.copy(color = DarkTextMuted, textAlign = TextAlign.Center),
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
            )
          }
        }
      }
    }

    // Live Front Camera Card
    item {
      Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("live_camera_card")
      ) {
        Column {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp)
              .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
          ) {
            val babyMonitorVm: BabyMonitorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val webRtcState by babyMonitorVm.connectionState.collectAsState()

            if (isConnected || webRtcState == WebRtcConnectionState.CONNECTED || webRtcState == WebRtcConnectionState.CONNECTING) {
              // Real Google WebRTC SurfaceViewRenderer stream with CameraX fallback
              WebRtcVideoPlayer(
                webRtcManager = babyMonitorVm.webRtcManager,
                connectionState = if (isConnected) WebRtcConnectionState.CONNECTED else webRtcState,
                isLocalView = true,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = null,
                tint = DarkPrimary.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
              )
            }

            // Status Badge
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (isConnected) LiveIndicatorRed else DarkTextMuted)
              )
              Text(
                text = if (isConnected) "ÖN KAMERA WEBRTC YAYINDA" else "CANLI YAYIN BEKLEMEDE",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              )
            }

            // Connect Button
            Button(
              onClick = onConnectToggle,
              colors = ButtonDefaults.buttonColors(
                containerColor = DarkPrimary,
                contentColor = DarkOnPrimary
              ),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("connect_camera_button")
            ) {
              Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = if (isConnected) "Yayın Kapat" else "Ön Kamera İzle", fontWeight = FontWeight.SemiBold)
            }
          }

          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Bebek Ön Kamerası (WebRTC Canlı Stream)",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                color = DarkTextPrimary
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = if (isConnected) "WebRTC Peer connection aktif • Ön kamera görüntüsü iletiliyor" else "Vercel Signaling üzerinden ön kamera eşleşmesi bekleniyor",
              style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
            )
          }
        }
      }
    }

    // Quick Stats Grid
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
          modifier = Modifier
            .weight(1f)
            .testTag("stat_card_battery")
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Battery5Bar, contentDescription = null, tint = DarkPrimary)
              Text("%84", style = MaterialTheme.typography.titleMedium.copy(color = DarkPrimary, fontWeight = FontWeight.Bold))
            }
            Text("Cihaz Şarjı", style = MaterialTheme.typography.bodySmall.copy(color = DarkTertiary))
          }
        }

        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = DarkSurface),
          modifier = Modifier
            .weight(1f)
            .clickable { onMicToggle() }
            .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
            .testTag("stat_card_mic")
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = null,
                tint = if (isMicActive) StatusGreen else LiveIndicatorRed
              )
              Text(
                text = if (isMicActive) "Aktif" else "Kapalı",
                style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Medium)
              )
            }
            Text("Ses Yayın (WebRTC)", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
          }
        }
      }
    }

    // Activity Log Section
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("activity_logs_card")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "SON AKTİVİTELER",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.2.sp,
              color = DarkTextMuted
            )
          )

          activityLogs.forEach { log ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(DarkBorder),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = log.icon,
                  contentDescription = null,
                  tint = DarkTextPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = log.title,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = DarkTextPrimary
                  )
                )
                Text(
                  text = log.subtitle,
                  style = MaterialTheme.typography.bodySmall.copy(color = DarkTextMuted)
                )
              }
              Text(
                text = log.time,
                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextMuted)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun LocationMapScreen(gpsLocation: LiveGpsLocation) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = DarkSurface),
    modifier = Modifier
      .fillMaxSize()
      .testTag("location_map_screen")
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(DarkPrimary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = DarkPrimary,
            modifier = Modifier.size(48.dp)
          )
        }
        Text(
          text = "Canlı GPS Konum Takip Haritası",
          style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
        )
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Enlem (Latitude):", color = DarkTextSecondary, fontSize = 13.sp)
              Text("%.6f° N".format(gpsLocation.latitude), color = DarkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Boylam (Longitude):", color = DarkTextSecondary, fontSize = 13.sp)
              Text("%.6f° E".format(gpsLocation.longitude), color = DarkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("GPS Hassasiyeti:", color = DarkTextSecondary, fontSize = 13.sp)
              Text("±%.1f metre".format(gpsLocation.accuracy), color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
        Text(
          text = "GPS Konum verileri WebRTC kanalı üzerinden canlı senkronize edilir.",
          style = MaterialTheme.typography.bodySmall.copy(color = DarkTextMuted, textAlign = TextAlign.Center)
        )
      }
    }
  }
}

@Composable
fun HistoryLogsScreen(activityLogs: List<ActivityItem>) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("history_logs_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items(activityLogs) { item ->
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(item.icon, contentDescription = null, tint = DarkPrimary)
          Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = DarkTextPrimary, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = DarkTextMuted, fontSize = 12.sp)
          }
          Text(item.time, color = DarkTextSecondary, fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
fun SettingsScreen() {
  var vercelEndpoint by remember { mutableStateOf("https://webrtc-signaling-server.vercel.app/api/signal") }
  var testStatusText by remember { mutableStateOf("") }
  val coroutineScope = rememberCoroutineScope()

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = DarkSurface),
    modifier = Modifier
      .fillMaxSize()
      .testTag("settings_screen")
  ) {
    LazyColumn(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Text(
          text = "WebRTC & Vercel Signaling Ayarları",
          style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
        )
      }

      item {
        HorizontalDivider(color = DarkBorder)
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Bebek Ön Kamerası WebRTC Modu", color = DarkTextPrimary, fontWeight = FontWeight.Medium)
            Text("Sadece ön kamerayı eşleştirme koduyla canlı yayına al", color = DarkTextMuted, fontSize = 11.sp)
          }
          Switch(
            checked = true,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(
              checkedThumbColor = DarkOnPrimary,
              checkedTrackColor = DarkPrimary
            )
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Arka Plan Servis Bildirimi", color = DarkTextPrimary, fontWeight = FontWeight.Medium)
            Text("Uygulama kapalıyken saat ile bildirim çubuğunda canlı tut", color = DarkTextMuted, fontSize = 11.sp)
          }
          Switch(
            checked = true,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(
              checkedThumbColor = DarkOnPrimary,
              checkedTrackColor = DarkPrimary
            )
          )
        }
      }

      item {
        HorizontalDivider(color = DarkBorder)
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Vercel WebRTC Signaling Sunucusu",
            style = MaterialTheme.typography.titleSmall.copy(color = DarkPrimary, fontWeight = FontWeight.Bold)
          )
          OutlinedTextField(
            value = vercelEndpoint,
            onValueChange = { vercelEndpoint = it },
            label = { Text("Signaling Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Button(
            onClick = {
              coroutineScope.launch {
                testStatusText = "Vercel Signaling Test Ediliyor..."
                val success = WebRtcSignalingManager.connectParentWithCode("582914")
                testStatusText = if (success) "Vercel WebRTC Signaling Bağlantısı Başarılı!" else "Signaling Bağlantı Hatası"
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary, contentColor = DarkOnPrimary),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Signaling Sunucusunu Test Et", fontWeight = FontWeight.Bold)
          }

          if (testStatusText.isNotEmpty()) {
            Text(testStatusText, color = StatusGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

