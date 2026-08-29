import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class TestParse {
    static class IptvChannel {
        String name;
        String url;
        public IptvChannel(String name, String url) { this.name = name; this.url = url; }
        public String toString() { return name + " -> " + url; }
    }

    public static void main(String[] args) throws Exception {
        String rawM3u = "#EXTINF:-1,72 Hours (2026) \uD83C\uDD95\n" +
                "http://ftp.ctgfun.com/English/72%20Hours%20%282026%29%201080p%20WEBRip%20x264%20ESub%20%5BDDN%5D/72%20Hours%20%282026%29%201080p%20WEBRip%20x264%20ESub%20%5BDDN%5D.mp4\n" +
                "#EXTINF:-1,SuperGirl (2026) \uD83C\uDD95\n" +
                "http://ftp.ctgfun.com/English/Supergirl%20%282026%29%201080p%20WEBRip%20x264%20ESub%20%5BDDN%5D/Supergirl%20%282026%29%201080p%20WEBRip%20x264%20ESub%20%5BDDN%5D.mp4";

        List<IptvChannel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(rawM3u));
        String currentName = "";
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#EXTINF:")) {
                int commaIndex = trimmed.lastIndexOf(',');
                if (commaIndex != -1 && commaIndex < trimmed.length() - 1) {
                    currentName = trimmed.substring(commaIndex + 1).trim();
                }
            } else if (trimmed.startsWith("http")) {
                channels.add(new IptvChannel(currentName, trimmed));
                currentName = "";
            }
        }
        
        for (IptvChannel c : channels) {
            System.out.println(c);
        }
    }
}
