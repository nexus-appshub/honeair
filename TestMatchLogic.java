import java.util.regex.Pattern;
import java.util.List;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;

public class TestMatchLogic {

    static class IptvChannel {
        String name;
        String url;
        public IptvChannel(String name, String url) { this.name = name; this.url = url; }
    }

    public static String normalizeMediaTitle(String raw) {
        String val = raw.toLowerCase();
        val = val.replaceAll("\\(.*?\\)", "");
        val = val.replaceAll("\\[.*?\\]", "");
        val = val.replaceAll("[^a-z0-9\u0980-\u09ff]", " ");
        val = val.replaceAll("\\s+", " ");
        return val.trim();
    }

    public static boolean isMatch(IptvChannel channel, String itemTitle) {
        String cleanItemTitle = normalizeMediaTitle(itemTitle);
        String cleanChannelName = normalizeMediaTitle(channel.name);

        if (cleanItemTitle.length() > 0 && cleanChannelName.length() > 0) {
            if (cleanChannelName.equals(cleanItemTitle)) return true;
            if (cleanItemTitle.length() >= 3 && cleanChannelName.length() >= 3) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    return true;
                }
            }
            String[] itemWords = cleanItemTitle.split(" ");
            String[] channelWords = cleanChannelName.split(" ");
            boolean allMatch = true;
            for (String iw : itemWords) {
                if (iw.trim().isEmpty()) continue;
                boolean found = false;
                for (String cw : channelWords) {
                    if (cw.equals(iw)) { found = true; break; }
                }
                if (!found) { allMatch = false; break; }
            }
            if (allMatch && itemWords.length > 0) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        
        List<IptvChannel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        String currentName = "";
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
        reader.close();

        String testTitle = "72 Hours";
        boolean found = false;
        for (IptvChannel c : channels) {
            if (isMatch(c, testTitle)) {
                System.out.println("MATCHED: " + c.name + " with " + testTitle);
                found = true;
                break;
            }
        }
        if (!found) {
            System.out.println("NO MATCH for " + testTitle);
        }
    }
}
