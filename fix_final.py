with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    lines = f.readlines()

# add a `}` at line 862
lines.insert(861, "}\n")

# remove lines 981, 982, 983, 984 (which are now shifted by +1 due to the insert, so 982-985)
# let's just strip trailing empty lines and } at the very end

while lines[-1].strip() in ["", "}"]:
    lines.pop()

# RelatedMediaCard needs exactly one `}` at the end
# Let's verify by just appending `}\n` because we popped all of them.
lines.append("}\n")

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.writelines(lines)
