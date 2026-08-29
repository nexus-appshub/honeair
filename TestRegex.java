import java.util.regex.Pattern;

public class TestRegex {
    public static void main(String[] args) {
        String raw = "72 Hours (2026) 🆕".toLowerCase();
        System.out.println("1: " + raw);
        raw = raw.replaceAll("\\(.*?\\)", ""); // Non-greedy
        System.out.println("2: " + raw);
        raw = raw.replaceAll("\\[.*?\\]", "");
        System.out.println("3: " + raw);
        raw = raw.replaceAll("[^a-z0-9\u0980-\u09ff]", " ");
        System.out.println("4: " + raw);
        raw = raw.replaceAll("\\s+", " ");
        System.out.println("5: '" + raw.trim() + "'");
    }
}
