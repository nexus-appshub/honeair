import re

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    content = f.read()

# Pattern 1 (Top Left)
pattern1 = r"""                // Premium VIP Badge Overlay Top Left
                if \(item\.isPremium\) \{
                    Box\(
                        modifier = Modifier
                            \.padding\(4\.dp\)
                            \.align\(Alignment\.TopStart\)
                            \.background\(
                                Brush\.linearGradient\(listOf\(Color\(0xFFFFD700\), Color\(0xFFFF8C00\)\)\),
                                RoundedCornerShape\(4\.dp\)
                            \)
                            \.padding\(horizontal = 4\.dp, vertical = 2\.dp\)
                    \) \{
                        Row\(verticalAlignment = Alignment\.CenterVertically\) \{
                            Icon\(
                                imageVector = Icons\.Default\.WorkspacePremium,
                                contentDescription = "VIP",
                                tint = Color\.Black,
                                modifier = Modifier\.size\(9\.dp\)
                            \)
                            Spacer\(modifier = Modifier\.width\(2\.dp\)\)
                            Text\(
                                text = "VIP",
                                color = Color\.Black,
                                fontSize = 8\.sp,
                                fontWeight = FontWeight\.ExtraBold
                            \)
                        \}
                    \}
                \}"""

replacement1 = """                // Premium VIP Badge Overlay Top Left
                if (item.isPremium) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                            .align(Alignment.TopStart)
                    )
                }"""

content = re.sub(pattern1, replacement1, content)

# Pattern 2 (Top End)
pattern2 = r"""                // Premium VIP Badge Overlay
                if \(item\.isPremium\) \{
                    Box\(
                        modifier = Modifier
                            \.padding\(6\.dp\)
                            \.align\(Alignment\.TopEnd\)
                            \.background\(
                                Brush\.linearGradient\(listOf\(Color\(0xFFFFD700\), Color\(0xFFFF8C00\)\)\),
                                RoundedCornerShape\(4\.dp\)
                            \)
                            \.padding\(horizontal = 5\.dp, vertical = 2\.dp\)
                    \) \{
                        Row\(verticalAlignment = Alignment\.CenterVertically\) \{
                            Icon\(
                                imageVector = Icons\.Default\.WorkspacePremium,
                                contentDescription = "VIP",
                                tint = Color\.Black,
                                modifier = Modifier\.size\(10\.dp\)
                            \)
                            Spacer\(modifier = Modifier\.width\(2\.dp\)\)
                            Text\(
                                text = "VIP",
                                color = Color\.Black,
                                fontSize = 9\.sp,
                                fontWeight = FontWeight\.ExtraBold
                            \)
                        \}
                    \}
                \}"""

replacement2 = """                // Premium VIP Badge Overlay
                if (item.isPremium) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                    )
                }"""

content = re.sub(pattern2, replacement2, content)

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "w") as f:
    f.write(content)
