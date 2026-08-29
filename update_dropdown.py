import re

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    text = f.read()

# Let's find DropdownMenu( expanded = showNotificationPopup ... )
start_pos = text.find("DropdownMenu(\n                            expanded = showNotificationPopup")
if start_pos == -1:
    start_pos = text.find("DropdownMenu(\n")

# Let's find the matching closing parenthesis / brace for DropdownMenu
# Alternatively, since we know the exact structure, let's find where "val profile = userProfile" starts after it.
end_pos = text.find("val profile = userProfile", start_pos)

print(f"Start: {start_pos}, End: {end_pos}")

if start_pos != -1 and end_pos != -1:
    old_block = text[start_pos:end_pos]
    
    new_dropdown = """DropdownMenu(
                            expanded = showNotificationPopup,
                            onDismissRequest = { showNotificationPopup = false },
                            offset = androidx.compose.ui.unit.DpOffset(x = (-130).dp, y = 0.dp),
                            modifier = Modifier
                                .width(340.dp)
                                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                                .border(1.dp, homeBorderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 450.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Notification Center",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = homeTextColor,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                HorizontalDivider(color = homeBorderColor)

                                val activeDownloadsList = activeDownloads
                                if (activeDownloadsList.isNotEmpty()) {
                                    Text("Active Downloads", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
                                    for (download in activeDownloadsList) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                                    CircularProgressIndicator(
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

                                Text("System Alerts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonPurple, modifier = Modifier.padding(top = 4.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth().clickable { showNotificationPopup = false; onNavigateToAirTab() }
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

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth().clickable { showNotificationPopup = false }
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
                    Spacer(modifier = Modifier.width(2.dp))
                    
    """
    
    text = text[:start_pos] + new_dropdown + text[end_pos:]
    with open("app/src/main/java/com/example/ui/screens/Screens.kt", "w") as f:
        f.write(text)
    print("SUCCESS")
else:
    print("FAILED TO FIND POSITIONS")
