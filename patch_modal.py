import re

with open("app/src/main/java/com/example/ui/components/SubscriptionPlanModal.kt", "r") as f:
    content = f.read()

target_pattern = r"""                        // Live Payment Gateways preview display if available
                        val paymentGateways = liveVipConfig\?\.paymentGateways
                        if \(paymentGateways != null\) \{
                            Spacer\(modifier = Modifier\.height\(12\.dp\)\)
                            Surface\(
                                shape = RoundedCornerShape\(12\.dp\),
                                color = Color\(0xFF141923\),
                                border = BorderStroke\(1\.dp, Color\(0xFF242E42\)\),
                                modifier = Modifier\.fillMaxWidth\(\)
                            \) \{
                                Column\(modifier = Modifier\.padding\(10\.dp\)\) \{
                                    Text\(
                                        text = "Available In-App Payment Methods:",
                                        fontSize = 11\.sp,
                                        fontWeight = FontWeight\.Bold,
                                        color = Color\(0xFFFFB300\)
                                    \)
                                    Spacer\(modifier = Modifier\.height\(4\.dp\)\)
                                    Row\(
                                        modifier = Modifier\.fillMaxWidth\(\),
                                        horizontalArrangement = Arrangement\.spacedBy\(10\.dp\)
                                    \) \{
                                        paymentGateways\.bkash\?\.let \{
                                            Text\("bKash", fontSize = 10\.sp, color = Color\.White, fontWeight = FontWeight\.Bold\)
                                        \}
                                        paymentGateways\.nagad\?\.let \{
                                            Text\("Nagad", fontSize = 10\.sp, color = Color\.White, fontWeight = FontWeight\.Bold\)
                                        \}
                                        paymentGateways\.rocket\?\.let \{
                                            Text\("Rocket", fontSize = 10\.sp, color = Color\.White, fontWeight = FontWeight\.Bold\)
                                        \}
                                    \}
                                \}
                            \}
                        \}

                        Spacer\(modifier = Modifier\.height\(18\.dp\)\)

                        // Action Button
                        Button\(
                            onClick = \{
                                showPaymentGuideStep = true
                            \},
                            modifier = Modifier
                                \.fillMaxWidth\(\)
                                \.height\(48\.dp\),
                            shape = RoundedCornerShape\(12\.dp\),
                            colors = ButtonDefaults\.buttonColors\(
                                containerColor = Color\(0xFFFFD700\),
                                contentColor = Color\.Black
                            \)
                        \) \{
                            Icon\(
                                imageVector = Icons\.Default\.WorkspacePremium,
                                contentDescription = null,
                                tint = Color\.Black,
                                modifier = Modifier\.size\(20\.dp\)
                            \)
                            Spacer\(modifier = Modifier\.width\(8\.dp\)\)
                            Text\(
                                text = if \(isExpired\) "Renew Now \(\$\{chosenPlan\.price\}\)" else "Subscribe Now \(\$\{chosenPlan\.price\}\)",
                                fontWeight = FontWeight\.Bold,
                                fontSize = 14\.sp
                            \)
                        \}"""


replacement = """                        // Check if mobile payments are disabled via admin
                        val isMobilePaymentEnabled = liveVipConfig?.isMobilePaymentEnabled != false
                        
                        if (isMobilePaymentEnabled) {
                            // Live Payment Gateways preview display if available
                            val paymentGateways = liveVipConfig?.paymentGateways
                            if (paymentGateways != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF141923),
                                    border = BorderStroke(1.dp, Color(0xFF242E42)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Available In-App Payment Methods:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFB300)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            paymentGateways.bkash?.let {
                                                Text("bKash", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            paymentGateways.nagad?.let {
                                                Text("Nagad", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            paymentGateways.rocket?.let {
                                                Text("Rocket", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Display admin notes if mobile payment is disabled
                            val disabledNote = liveVipConfig?.mobilePaymentDisabledNote ?: "In-App mobile payments are currently disabled. Please click below to complete your payment securely on our official website."
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2C1A30), // soft purple/reddish tint
                                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.Info, contentDescription = "Notice", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Notice", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = disabledNote,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Button
                        Button(
                            onClick = {
                                if (isMobilePaymentEnabled) {
                                    showPaymentGuideStep = true
                                } else {
                                    val urlToOpen = liveVipConfig?.externalPaymentUrl ?: checkoutUrl
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (isMobilePaymentEnabled) Icons.Default.WorkspacePremium else androidx.compose.material.icons.Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMobilePaymentEnabled) {
                                    if (isExpired) "Renew Now (${chosenPlan.price})" else "Subscribe Now (${chosenPlan.price})"
                                } else {
                                    "Pay on Website"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }"""

if re.search(target_pattern, content, flags=re.DOTALL):
    content = re.sub(target_pattern, replacement, content, flags=re.DOTALL)
    with open("app/src/main/java/com/example/ui/components/SubscriptionPlanModal.kt", "w") as f:
        f.write(content)
    print("Patch applied successfully")
else:
    print("Pattern not found!")
