import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# Replace SR-1 with Vidnest Fun
content = re.sub(r'Pair\("SR-1",', r'Pair("Vidnest Fun",', content)
content = re.sub(r'Pair\("SR-2",', r'Pair("Vidsrc Sbs",', content)
content = re.sub(r'Pair\("SR-3",', r'Pair("Vidrock To",', content)
content = re.sub(r'Pair\("SR-4",', r'Pair("Vidsrc To",', content)
content = re.sub(r'Pair\("SR-5",', r'Pair("Vidlink Pro",', content)
content = re.sub(r'Pair\("SR-6",', r'Pair("Vidsrc Me",', content)
content = re.sub(r'Pair\("SR-7",', r'Pair("Vidsrc Net",', content)
content = re.sub(r'Pair\("SR-8",', r'Pair("Autoembed Co",', content)
content = re.sub(r'Pair\("SR-9",', r'Pair("Embed Su",', content)
content = re.sub(r'Pair\("SR-10",', r'Pair("Multiembed Mov",', content)
content = re.sub(r'Pair\("SR-11",', r'Pair("Vidsrc Pro",', content)
content = re.sub(r'Pair\("SR-12",', r'Pair("Moviesapi Club",', content)
content = re.sub(r'Pair\("SR-13",', r'Pair("Vidsrc In",', content)
content = re.sub(r'Pair\("SR-14",', r'Pair("Vidzee Wtf",', content)
content = re.sub(r'Pair\("SR-15",', r'Pair("Smashy Stream",', content)
content = re.sub(r'Pair\("SR-16",', r'Pair("Anyembed Com",', content)
content = re.sub(r'Pair\("SR-17",', r'Pair("Vidlink API",', content)
content = re.sub(r'Pair\("SR-18",', r'Pair("Videasy Net",', content)
content = re.sub(r'Pair\("SR-19",', r'Pair("Vidsrc Embed Ru",', content)
content = re.sub(r'Pair\("SR-20",', r'Pair("Vsrc Su",', content)
content = re.sub(r'Pair\("SR-21",', r'Pair("Cine Su",', content)
content = re.sub(r'Pair\("SR-22",', r'Pair("02moviedownloader",', content)
content = re.sub(r'Pair\("SR-23",', r'Pair("Fmovies4u Com",', content)
content = re.sub(r'Pair\("SR-24",', r'Pair("Vixsrc To",', content)
content = re.sub(r'Pair\("SR-25",', r'Pair("Vidsrc2 Ru",', content)
content = re.sub(r'Pair\("SR-26",', r'Pair("Server 26",', content)
content = re.sub(r'Pair\("SR-27",', r'Pair("Vidsrc Vip",', content)
content = re.sub(r'Pair\("SR-28",', r'Pair("Vidsrc Nl",', content)
content = re.sub(r'Pair\("SR-29",', r'Pair("Multiembed Alt",', content)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
