import re

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    content = f.read()

target = """                        if (hasNewNotification) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = showNotificationPopup,
                            onDismissRequest = { showNotificationPopup = false },
                            offset = androidx.compose.ui.unit.DpOffset(x = (-130).dp, y = 0.dp),
                            modifier = Modifier
                                .width(300.dp)
                                .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                        ) {
                            Text(
                                text = "Notifications",
                                fontWeight = FontWeight.Bold,
                                color = homeTextColor,
                                modifier = Modifier.padding(16.dp)
                            )
                            HorizontalDivider(color = homeBorderColor)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("App Update Available", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = homeTextColor)
                                        Text("A new version of Home Air TV is available in the Apps Hub. Get the latest features!", fontSize = 12.sp, color = homeSubTextColor)
                                    }
                                },
                                onClick = {
                                    showNotificationPopup = false
                                    onNavigateToAirTab()
                                }
                            )
                            HorizontalDivider(color = homeBorderColor)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Welcome to Home Air TV", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = homeTextColor)
                                        Text("Enjoy seamless streaming with zero lag.", fontSize = 12.sp, color = homeSubTextColor)
                                    }
                                },
                                onClick = { showNotificationPopup = false }
                            )
                        }"""

replacement = """                        val activeDownloads by com.example.download.MediaDownloader.activeDownloads.collectAsState()
                        if (hasNewNotification || activeDownloads.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (activeDownloads.isNotEmpty()) NeonCyan else Color(0xFFFF3B30)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeDownloads.isNotEmpty()) activeDownloads.size.toString() else "1",
                                    color = if (activeDownloads.isNotEmpty()) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showNotificationPopup,
                            onDismissRequest = { showNotificationPopup = false },
                            offset = androidx.compose.ui.unit.DpOffset(x = (-130).dp, y = 0.dp),
                            modifier = Modifier
                                .width(340.dp)
                                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                                .border(1.dp, homeBorderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                                Text(
                                    text = "Notification Center",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = homeTextColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = homeBorderColor)
                                
                                val activeDownloadsList = activeDownloads
                                
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (activeDownloadsList.isNotEmpty()) {
                                        item {
                                            Text("Active Downloads", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan, modifier = Modifier.padding(bottom = 4.dp))
                                        }
                                        items(activeDownloadsList.size) { index ->
                                            val download = activeDownloadsList[index]
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                                        androidx.compose.material3.CircularProgressIndicator(
                                                            progress = { download.progress / 100f },
                                                            color = NeonCyan,
                                                            strokeWidth = 3.dp,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        Text("${download.progress}%", fontSize = 10.sp, color = homeTextColor, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(download.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = homeTextColor)
                                                        Text(if (download.isCancelled) "Cancelled" else "Downloading...", fontSize = 11.sp, color = homeSubTextColor)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = { com.example.download.MediaDownloader.cancelDownload(download.id) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    item {
                                        Text("System Alerts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonPurple, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                                            modifier = Modifier.clickable { showNotificationPopup = false; onNavigateToAirTab() }
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonPurple.copy(alpha=0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("App Update Available", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = homeTextColor)
                                                    Text("A new version is available.", fontSize = 11.sp, color = homeSubTextColor)
                                                }
                                            }
                                        }
                                    }
                                    
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.3f)),
                                            modifier = Modifier.clickable { showNotificationPopup = false }
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonMagenta.copy(alpha=0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(24.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Welcome to Home Air TV", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = homeTextColor)
                                                    Text("Enjoy seamless streaming.", fontSize = 11.sp, color = homeSubTextColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }"""

new_content = content.replace(target, replacement)
if new_content == content:
    print("NO MATCH FOUND")
else:
    with open("app/src/main/java/com/example/ui/screens/Screens.kt", "w") as f:
        f.write(new_content)
    print("REPLACED SUCCESSFULLY")
