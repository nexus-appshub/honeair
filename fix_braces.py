with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

old_str = """                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        // Floating Download Button"""

new_str = """                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        } // Close Column
        
        // Floating Download Button"""

content = content.replace(old_str, new_str)

# also remove the extra } } at the end
# because the syntax error was:
# e: file:///app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt:980:1 Syntax error: Expecting a top level declaration.

content = content.replace("    }\n    }\n}\n}\n}", "    }\n    }\n}")

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(content)
