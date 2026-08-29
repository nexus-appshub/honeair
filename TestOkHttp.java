import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestOkHttp {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        System.out.println("Response Code: " + responseCode);
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            int count = 0;
            while ((inputLine = in.readLine()) != null) {
                if (count < 5) System.out.println(inputLine);
                count++;
            }
            in.close();
            System.out.println("Total lines: " + count);
        }
    }
}
